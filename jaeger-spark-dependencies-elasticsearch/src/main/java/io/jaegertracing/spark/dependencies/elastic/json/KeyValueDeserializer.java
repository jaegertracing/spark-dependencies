/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.jaegertracing.spark.dependencies.model.KeyValue;
import java.io.IOException;

/**
 * @author Pavol Loffay
 */
public class KeyValueDeserializer extends StdDeserializer<KeyValue> {

  // TODO Spark incorrectly serializes object mapper, therefore reinitializing here
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  public KeyValueDeserializer() {
    super(KeyValue.class);
  }

  @Override
  public KeyValue deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    String key = node.get("key").asText();
    String type = node.get("type").asText();

    KeyValue keyValue = new KeyValue();
    keyValue.setKey(key);
    keyValue.setValueType(type);

    if ("string".equalsIgnoreCase(type)) {
      keyValue.setValueString(node.get("value").asText());
    }

    return keyValue;
  }
}
