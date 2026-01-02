/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.json.JsonHelper;
import io.jaegertracing.spark.dependencies.model.Span;
import org.apache.spark.api.java.function.Function;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 */
public class ElasticTupleToSpan implements Function<Tuple2<String, String>, Span> {

  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  @Override
  public Span call(Tuple2<String, String> tuple) throws Exception {
    Span span = objectMapper.readValue(tuple._2(), Span.class);
    String originalTraceId = span.getTraceId();
    span.setTraceId(normalizeTraceId(originalTraceId));
    if (span.getTags() != null) {
      span.getTags().sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));
    }
    if (span.getRefs() != null) {
      span.getRefs().sort((o1, o2) -> o1.getSpanId().compareTo(o2.getSpanId()));
    }

    return span;
  }

  private String normalizeTraceId(String traceId) {
    if (traceId != null && traceId.length() < 32) {
      return String.format("%32s", traceId).replace(' ', '0');
    }
    return traceId;
  }
}
