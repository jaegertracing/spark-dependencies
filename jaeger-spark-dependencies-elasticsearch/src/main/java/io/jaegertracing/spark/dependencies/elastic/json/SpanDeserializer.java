/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.jaegertracing.spark.dependencies.model.KeyValue;
import io.jaegertracing.spark.dependencies.model.Process;
import io.jaegertracing.spark.dependencies.model.Reference;
import io.jaegertracing.spark.dependencies.model.Span;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Pavol Loffay
 */
public class SpanDeserializer extends StdDeserializer<Span> {

  // TODO Spark incorrectly serializes object mapper, therefore reinitializing here
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());
  private static final Logger log = LoggerFactory.getLogger(SpanDeserializer.class);

  public SpanDeserializer() {
    super(Span.class);
  }

  @Override
  public Span deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    String spanIdHex = null;
    JsonNode spanIdNode = node.get("spanID");
    if (spanIdNode != null && !spanIdNode.isNull()) {
      spanIdHex = spanIdNode.asText();
    }
    String traceIdHex = null;
    JsonNode traceIdNode = node.get("traceID");
    if (traceIdNode != null && !traceIdNode.isNull()) {
      traceIdHex = traceIdNode.asText();
    }

    // Try startTimeMillis first (Jaeger v2), fallback to startTime (v1)
    Long startTime = null;
    JsonNode startTimeMillisNode = node.get("startTimeMillis");
    if (startTimeMillisNode != null && !startTimeMillisNode.isNull()) {
      startTime = startTimeMillisNode.asLong();
    } else {
      JsonNode startTimeNode = node.get("startTime");
      if (startTimeNode != null && !startTimeNode.isNull()) {
        try {
          startTime = Long.parseLong(startTimeNode.asText());
        } catch (NumberFormatException e) {
          log.warn("Failed to parse startTime: {}", startTimeNode.asText());
        }
      }
    }

    // Process (null-safe)
    Process process = null;
    JsonNode processNode = node.get("process");
    if (processNode != null && !processNode.isNull()) {
      try {
        process = objectMapper.treeToValue(processNode, Process.class);
      } catch (Exception e) {
        log.warn("Failed to deserialize process for span {}: {}", spanIdHex, e.getMessage());
      }
    }

    // Tags (null-safe)
    List<KeyValue> tags = new ArrayList<>();
    JsonNode tagsNode = node.get("tags");
    if (tagsNode != null && !tagsNode.isNull() && tagsNode.isArray()) {
      try {
        KeyValue[] tagsArray = objectMapper.treeToValue(tagsNode, KeyValue[].class);
        if (tagsArray != null) {
          tags.addAll(Arrays.asList(tagsArray));
        }
      } catch (Exception e) {
        log.warn("Failed to deserialize tags for span {}: {}", spanIdHex, e.getMessage());
      }
    }

    // Legacy nested tag object ("tag") support - optional
    JsonNode tagFieldsNode = node.get("tag");
    if (tagFieldsNode != null && !tagFieldsNode.isNull()) {
      try {
        Map<String, Object> tagFields = objectMapper.treeToValue(tagFieldsNode, Map.class);
        tags = addTagFields(tags, tagFields);
      } catch (Exception e) {
        log.warn("Failed to deserialize 'tag' fields for span {}: {}", spanIdHex, e.getMessage());
      }
    }

    // References (null-safe)
    List<Reference> references = deserializeReferences(node, spanIdHex);

    Span span = new Span();
    // spanId
    if (spanIdHex != null && !spanIdHex.isEmpty()) {
      try {
        span.setSpanId(new BigInteger(spanIdHex, 16).longValue());
      } catch (NumberFormatException e) {
        log.warn("Failed to parse spanID: {}", spanIdHex);
      }
    }
    // traceId
    if (traceIdHex != null) {
      span.setTraceId(traceIdHex);
    }
    span.setRefs(references);
    span.setStartTime(startTime);
    span.setProcess(process);
    span.setTags(tags);
    return span;
  }

  private List<KeyValue> addTagFields(List<KeyValue> tags, Map<String, Object> tagFields) {
    ArrayList<KeyValue> result = new ArrayList<>(tags.size() + (tagFields != null ? tagFields.size() : 0));
    result.addAll(tags);
    if (tagFields != null) {
      List<KeyValue> collect = tagFields.entrySet().stream().map(stringObjectEntry -> {
        KeyValue kv = new KeyValue();
        kv.setKey(stringObjectEntry.getKey());
        kv.setValueString(stringObjectEntry.getValue() != null ? stringObjectEntry.getValue().toString() : null);
        return kv;
      }).collect(Collectors.toList());
      result.addAll(collect);
    }
    return result;
  }

  private List<Reference> deserializeReferences(JsonNode node, String spanIdHex) throws JsonProcessingException {
    List<Reference> references = new ArrayList<>();

    // Legacy parentSpanID
    JsonNode parentSpanIDNode = node.get("parentSpanID");
    if (parentSpanIDNode != null && !parentSpanIDNode.isNull()) {
      String parentSpanIDStr = parentSpanIDNode.asText();
      if (parentSpanIDStr != null && !parentSpanIDStr.isEmpty()) {
        try {
          BigInteger bigInteger = new BigInteger(parentSpanIDStr, 16);
          Reference reference = new Reference();
          reference.setSpanId(bigInteger.longValue());
          references.add(reference);
        } catch (NumberFormatException e) {
          log.warn("Failed to parse parentSpanID: {} for span {}", parentSpanIDStr, spanIdHex);
        }
      }
    }

    // Modern references array
    JsonNode referencesNode = node.get("references");
    if (referencesNode != null && !referencesNode.isNull() && referencesNode.isArray()) {
      try {
        Reference[] referencesArr = objectMapper.treeToValue(referencesNode, Reference[].class);
        if (referencesArr != null) {
          references.addAll(Arrays.asList(referencesArr));
        }
      } catch (Exception e) {
        log.warn("Failed to deserialize references for span {}: {}", spanIdHex, e.getMessage());
      }
    }

    return references;
  }
}
