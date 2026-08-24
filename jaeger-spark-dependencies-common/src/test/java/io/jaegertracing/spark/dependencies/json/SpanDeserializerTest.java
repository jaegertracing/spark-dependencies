/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.json;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.model.Span;
import org.junit.Test;

public class SpanDeserializerTest {

  private final ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  @Test
  public void shouldNotDuplicateParentWhenParentSpanIdAndReferencesBothPresent() throws Exception {
    String json = "{"
        + "\"traceID\":\"1\","
        + "\"spanID\":\"2\","
        + "\"parentSpanID\":\"1\","
        + "\"startTime\":\"1\","
        + "\"process\":{\"serviceName\":\"S2\"},"
        + "\"tags\":[],"
        + "\"references\":[{\"refType\":\"CHILD_OF\",\"traceID\":\"1\",\"spanID\":\"1\"}]"
        + "}";

    Span span = objectMapper.readValue(json, Span.class);

    assertEquals(1, span.getRefs().size());
    assertEquals(1L, span.getRefs().get(0).getSpanId().longValue());
  }

  @Test
  public void shouldReadParentFromParentSpanIdWhenReferencesMissing() throws Exception {
    String json = "{"
        + "\"traceID\":\"1\","
        + "\"spanID\":\"2\","
        + "\"parentSpanID\":\"1\","
        + "\"startTime\":\"1\","
        + "\"process\":{\"serviceName\":\"S2\"},"
        + "\"tags\":[]"
        + "}";

    Span span = objectMapper.readValue(json, Span.class);

    assertEquals(1, span.getRefs().size());
    assertEquals(1L, span.getRefs().get(0).getSpanId().longValue());
  }

  @Test
  public void shouldIgnoreAllZeroParentSpanId() throws Exception {
    String json = "{"
        + "\"traceID\":\"1\","
        + "\"spanID\":\"2\","
        + "\"parentSpanID\":\"0000000000000000\","
        + "\"startTime\":\"1\","
        + "\"process\":{\"serviceName\":\"S1\"},"
        + "\"tags\":[],"
        + "\"references\":[]"
        + "}";

    Span span = objectMapper.readValue(json, Span.class);

    assertEquals(0, span.getRefs().size());
  }
}
