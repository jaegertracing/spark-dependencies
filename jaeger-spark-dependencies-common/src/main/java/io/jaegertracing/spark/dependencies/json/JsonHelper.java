/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.model.KeyValue;
import io.jaegertracing.spark.dependencies.model.Reference;
import io.jaegertracing.spark.dependencies.model.Span;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class JsonHelper {

  private JsonHelper() {
  }

  public static ObjectMapper configure(ObjectMapper objectMapper) {
    objectMapper.addMixIn(Span.class, SpanMixin.class);
    objectMapper.addMixIn(KeyValue.class, KeyValueMixin.class);
    objectMapper.addMixIn(Reference.class, ReferenceMixin.class);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
