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

/**
 * @author Pavol Loffay
 */
public class SpanDeserializer extends StdDeserializer<Span> {

  // TODO Spark incorrectly serializes object mapper, therefore reinitializing here
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  public SpanDeserializer() {
    super(Span.class);
  }

  @Override
  public Span deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    String spanIdHex = node.get("spanID").asText();
    String traceIdHex = node.get("traceID").asText();
    String startTimeStr = node.get("startTime").asText();

    JsonNode processNode = node.get("process");
    Process process = objectMapper.treeToValue(processNode, Process.class);

    JsonNode tagsNode = node.get("tags");
    List<KeyValue> tags = Arrays.asList(objectMapper.treeToValue(tagsNode, KeyValue[].class));

    JsonNode tagFieldsNode = node.get("tag");
    if (tagFieldsNode != null) {
      Map<String, Object> tagFields = objectMapper.treeToValue(tagFieldsNode, Map.class);
      tags = addTagFields(tags, tagFields);
    }

    Span span = new Span();
    span.setSpanId(new BigInteger(spanIdHex, 16).longValue());
    span.setTraceId(traceIdHex);
    span.setRefs(deserializeReferences(node));
    span.setStartTime(startTimeStr != null ? Long.parseLong(startTimeStr) : null);
    span.setProcess(process);
    span.setTags(tags);
    return span;
  }

  private List<KeyValue> addTagFields(List<KeyValue> tags, Map<String, Object> tagFields) {
    ArrayList<KeyValue> result = new ArrayList<>(tags.size() + tagFields.size());
    result.addAll(tags);
    List<KeyValue> collect = tagFields.entrySet().stream().map(stringObjectEntry -> {
      KeyValue kv = new KeyValue();
      kv.setKey(stringObjectEntry.getKey());
      kv.setValueString(stringObjectEntry.getValue().toString());
      return kv;
    }).collect(Collectors.toList());
    result.addAll(collect);
    return result;
  }

  private List<Reference> deserializeReferences(JsonNode node) throws JsonProcessingException {
    List<Reference> references = new ArrayList<>();
    JsonNode parentSpanID = node.get("parentSpanID");
    if (parentSpanID != null) {
      BigInteger bigInteger = new BigInteger(parentSpanID.asText(), 16);
      Reference reference = new Reference();
      reference.setSpanId(bigInteger.longValue());
      references.add(reference);
    }

    Reference[] referencesArr = objectMapper.treeToValue(node.get("references"), Reference[].class);
    references.addAll(Arrays.asList(referencesArr));

    return references;
  }
}
