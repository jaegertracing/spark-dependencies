/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
public class DependenciesSparkHelper {
  private DependenciesSparkHelper() {}

  /**
   * Derives dependency links based on supplied spans (e.g. multiple traces). If there is a link A->B
   * in multiple traces it will return just one {@link Dependency} link with a correct {@link Dependency#callCount}.
   * Note that RDDs are grouped on traceId so if a span contains multiple references from different traces
   * the job does not produce correct result.
   *
   * @param traceIdSpans <traceId, trace> {@link org.apache.spark.api.java.JavaRDD} with trace id and a collection of
   *                     spans with that traceId.
   * @return Aggregated dependency links for all traces.
   */
  public static List<Dependency> derive(JavaPairRDD<String, Iterable<Span>> traceIdSpans,String peerServiceTag) {
    return traceIdSpans.flatMapValues(new SpansToDependencyLinks(peerServiceTag))
        .values()
        .mapToPair(dependency -> new Tuple2<>(new Tuple2<>(dependency.getParent(), dependency.getChild()), dependency))
        .reduceByKey((v1, v2) -> new Dependency(v1.getParent(), v1.getChild(), v1.getCallCount() + v2.getCallCount()))
        .values()
        .collect();
  }
}
