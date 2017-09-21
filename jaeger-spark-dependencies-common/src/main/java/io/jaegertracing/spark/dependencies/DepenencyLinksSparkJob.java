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
