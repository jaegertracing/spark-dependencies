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
