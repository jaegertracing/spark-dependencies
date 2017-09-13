package io.jaegertracing.spark.dependencies.cassandra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.api.java.function.Function;

import io.jaegertracing.spark.dependencies.cassandra.model.Dependency;
import io.jaegertracing.spark.dependencies.cassandra.model.Span;

/**
 * @author Pavol Loffay
 */
public class DependencyLinks {

    public static Function<Iterable<Span>, Iterable<Dependency>> dependencyLinks() {
        return (Function<Iterable<Span>, Iterable<Dependency>>) spans -> {
            Map<Long, Span> spanMap = new HashMap<>();
            for (Span span : spans) {
                spanMap.put(span.getSpanId(), span);
            }

            List<Dependency> result = new ArrayList<>(spanMap.size()/2);
            for (Span span: spans) {
                if (span.getParentId() == null || span.getProcess() == null ||
                        span.getProcess().getServiceName() == null) {
                    continue;
                }

                Span parent = spanMap.get(span.getParentId());
                if (parent == null || parent.getProcess() == null ||
                        parent.getProcess().getServiceName() == null) {
                    continue;
                }
                result.add(new Dependency(parent.getProcess().getServiceName(),
                        span.getProcess().getServiceName()));
            }
            return result;
        };
    }
}
