/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.json;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class SpanDeserializer extends StdDeserializer<Span> {

  // TODO Spark incorrectly serializes object mapper, therefore reinitializing
  // here
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  public SpanDeserializer() {
    super(Span.class);
  }

  @Override
  public Span deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    JsonNode spanIdNode = node.get("spanID");
    JsonNode traceIdNode = node.get("traceID");
    JsonNode startTimeNode = node.get("startTime");

    if (spanIdNode == null || traceIdNode == null) {
      throw new JsonProcessingException("Missing required fields: spanID or traceID") {
      };
    }

    String spanIdHex = spanIdNode.asText();
    String traceIdHex = traceIdNode.asText();
    String startTimeStr = startTimeNode != null ? startTimeNode.asText() : null;

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
    // Jaeger ES/OS v2 writes the parent both as parentSpanID and as a CHILD_OF entry in
    // references (see jaegertracing/jaeger#8859). Counting both doubles call counts.
    Set<Reference> references = new LinkedHashSet<>();
    addParentSpanId(references, node.get("parentSpanID"));

    JsonNode referencesNode = node.get("references");
    if (referencesNode != null && !referencesNode.isNull() && referencesNode.isArray()) {
      Reference[] referencesArr = objectMapper.treeToValue(referencesNode, Reference[].class);
      if (referencesArr != null) {
        for (Reference reference : referencesArr) {
          if (isUsableSpanId(reference == null ? null : reference.getSpanId())) {
            references.add(reference);
          }
        }
      }
    }

    return new ArrayList<>(references);
  }

  private static void addParentSpanId(Set<Reference> references, JsonNode parentSpanID) {
    if (parentSpanID == null || parentSpanID.isNull()) {
      return;
    }
    String spanIdHex = parentSpanID.asText();
    if (spanIdHex == null || spanIdHex.isEmpty() || isAllZeroHex(spanIdHex)) {
      return;
    }
    Reference reference = new Reference();
    reference.setSpanId(new BigInteger(spanIdHex, 16).longValue());
    references.add(reference);
  }

  private static boolean isUsableSpanId(Long spanId) {
    return spanId != null && spanId != 0L;
  }

  private static boolean isAllZeroHex(String spanIdHex) {
    for (int i = 0; i < spanIdHex.length(); i++) {
      if (spanIdHex.charAt(i) != '0') {
        return false;
      }
    }
    return true;
  }
}
