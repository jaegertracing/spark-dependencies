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
package io.jaegertracing.spark.dependencies.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.elastic.json.JsonHelper;
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
      span.getTags().sort(java.util.Comparator.comparing(io.jaegertracing.spark.dependencies.model.KeyValue::getKey,
          java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
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
