package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.model.Span;

/**
 * @author Pavol Loffay
 */
public class JsonHelper {

  private JsonHelper() {}

  public static ObjectMapper configure(ObjectMapper objectMapper) {
    objectMapper.addMixIn(Span.class, SpanMixin.class);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }
}
