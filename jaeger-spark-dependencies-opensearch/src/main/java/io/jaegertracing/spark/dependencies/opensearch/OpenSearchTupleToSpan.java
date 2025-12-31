/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.opensearch.json.JsonHelper;
import io.jaegertracing.spark.dependencies.model.Span;
import java.util.Map;
import org.apache.spark.api.java.function.Function;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class OpenSearchTupleToSpan implements Function<Tuple2<String, Map<String, Object>>, Span> {

  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  @Override
  public Span call(Tuple2<String, Map<String, Object>> tuple) throws Exception {
    Span span = objectMapper.convertValue(tuple._2(), Span.class);
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
