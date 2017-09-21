package io.jaegertracing.spark.dependencies;

import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.api.java.function.Function;

/**
 * @author Pavol Loffay
 */
public class SpansToDependencyLinks implements Function<Iterable<Span>, Iterable<Dependency>>{

    /**
     * Derives dependency links based on supplied spans.
     *
     * @param spans trace
     * @return collection of dependency links, note that it contains duplicates
     * @throws Exception
     */
    @Override
    public Iterable<Dependency> call(Iterable<Span> spans) throws Exception {
        Map<Long, Span> spanMap = new HashMap<>();
        for (Span span: spans) {
            spanMap.put(span.getSpanId(), span);
        }

        List<Dependency> result = new ArrayList<>();
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

            result.add(new Dependency(parent.getProcess().getServiceName(), span.getProcess().getServiceName()));
        }
        return result;
    }
}
