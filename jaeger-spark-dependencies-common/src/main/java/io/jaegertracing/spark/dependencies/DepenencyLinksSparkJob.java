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
package io.jaegertracing.spark.dependencies;

import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.util.List;
import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 */
public class DepenencyLinksSparkJob {

  private DepenencyLinksSparkJob() {}

  /**
   * Derives dependency links based on supplied spans.
   *
   * @param traceIdSpans <traceId, trace>
   * @return Dependency links for given traces
   */
  public static List<Dependency> derive(JavaPairRDD<String, Iterable<Span>> traceIdSpans) {
    return traceIdSpans.flatMapValues(new SpansToDependencyLinks())
        .values()
        .mapToPair(dependency -> new Tuple2<>(new Tuple2<>(dependency.getParent(), dependency.getChild()), dependency))
        .reduceByKey((v1, v2) -> new Dependency(v1.getParent(), v1.getChild(), v1.getCallCount() + v2.getCallCount()))
        .values()
        .collect();
  }
}