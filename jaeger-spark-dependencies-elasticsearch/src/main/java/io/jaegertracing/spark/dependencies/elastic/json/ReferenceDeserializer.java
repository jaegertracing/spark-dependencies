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
import io.jaegertracing.spark.dependencies.model.Reference;
import java.io.IOException;
import java.math.BigInteger;

/**
 * @author Pavol Loffay
 */
public class ReferenceDeserializer extends StdDeserializer<Reference> {

  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  protected ReferenceDeserializer() {
    super(Reference.class);
  }

  @Override
  public Reference deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    String spanIdHex = node.get("spanID").asText();

    Reference reference = new Reference();
    reference.setSpanId(new BigInteger(spanIdHex, 16).longValue());
    return reference;
  }
}
