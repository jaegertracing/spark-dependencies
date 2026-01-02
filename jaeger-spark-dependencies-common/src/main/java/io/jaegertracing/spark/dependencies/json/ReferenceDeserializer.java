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
import io.jaegertracing.spark.dependencies.model.Reference;
import java.io.IOException;
import java.math.BigInteger;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
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
