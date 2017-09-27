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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.spark.api.java.function.Function;

/**
 * @author Pavol Loffay
 */
public class SpansToDependencyLinks implements Function<Iterable<Span>, Iterable<Dependency>>{

    /**
     * Derives dependency links based on supplied spans.
     *
     * @param trace trace
     * @return collection of dependency links, note that it contains duplicates
     * @throws Exception
     */
    @Override
    public Iterable<Dependency> call(Iterable<Span> trace) throws Exception {
        Map<Long, Set<OnlySpanIdsKey>> spanMap = new LinkedHashMap<>();
        for (Span span: trace) {
            Set<OnlySpanIdsKey> sharedSpans = spanMap.get(span.getSpanId());
            if (sharedSpans == null) {
                sharedSpans = new LinkedHashSet<>();
                spanMap.put(span.getSpanId(), sharedSpans);
            }
            sharedSpans.add(new OnlySpanIdsKey(span));
        }

        // Let's start with zipkin shared spans
        List<Dependency> result = sharedSpanDependencies(spanMap);

        for (Span span: trace) {
            if (span.getParentId() == null || span.getProcess() == null ||
                span.getProcess().getServiceName() == null) {
                continue;
            }

            // if current span is shared server span we don't want to create a link
            // because the link should be for client span
            if (existsSpanWhichStartedBefore(span, spanMap)) {
                continue;
            }

            Set<OnlySpanIdsKey> parents = spanMap.get(span.getParentId());
            if (parents != null) {
                if (parents.size() > 1) {
                    // multiple possible parents so parent is zipkin shared span
                    // therefore we choose the one with the greatest start time (e.g. server span)
                    // we cannot choose client span because it's not a true parent!
                    Span sharedParent = null;
                    for (OnlySpanIdsKey sharedSpan: parents) {
                        if (sharedParent == null || sharedSpan.span.getStartTime() > sharedParent.getStartTime()) {
                            sharedParent = sharedSpan.span;
                        }
                    }
                    result.add(new Dependency(sharedParent.getProcess().getServiceName(), span.getProcess().getServiceName()));
                } else {
                    // this is jaeger span or zipkin native (not shared!)
                    Span parent = parents.iterator().next().span;
                    if (parent.getProcess() == null || parent.getProcess().getServiceName() == null) {
                        continue;
                    }
                    result.add(new Dependency(parent.getProcess().getServiceName(), span.getProcess().getServiceName()));
                }
            }
        }
        return result;
    }

    /**
     * @param span span
     * @param spanMap span map, key is spanId and value set of spans
     * @return true if there is a span in the set which started before given span.
     */
    private boolean existsSpanWhichStartedBefore(Span span, Map<Long, Set<OnlySpanIdsKey>> spanMap) {
        Set<OnlySpanIdsKey> sharedSpans = spanMap.get(span.getSpanId());
        if (sharedSpans != null && sharedSpans.size() > 1) {
            for (OnlySpanIdsKey other: sharedSpans) {
                if (span.getStartTime() > other.span.getStartTime()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Dependency> sharedSpanDependencies(Map<Long, Set<OnlySpanIdsKey>> spanMap) {
        List<Dependency> dependencies = new ArrayList<>();
        // create links between shared spans
        spanMap.values().forEach(spans -> {
            List<OnlySpanIdsKey> spanList = new ArrayList<>(spans);
            for (int i = 0; i < spanList.size(); i++) {
                for (int j = i + 1; j < spanList.size(); j++) {
                    createLinkSharedSpan(spanList.get(i).span, spanList.get(j).span)
                        .ifPresent(dependency -> dependencies.add(dependency));
                }
            }
        });

        return dependencies;
    }

    private Optional<Dependency> createLinkSharedSpan(Span spanA, Span spanB) {
        // return empty if the span is not shared
        if (!spanA.getSpanId().equals(spanB.getSpanId())) {
            return Optional.empty();
        }

        Dependency dependency;
        if (spanA.getStartTime() < spanB.getStartTime()) {
            dependency = new Dependency(spanA.getProcess().getServiceName(), spanB.getProcess().getServiceName());
        } else {
            dependency = new Dependency(spanB.getProcess().getServiceName(), spanA.getProcess().getServiceName());
        }
        return Optional.of(dependency);
    }

    /**
     * Used in Set to eliminate the same spans reported in chunks.
     * So we compare only ids and process because of zipkin shared spans.
     */
    private class OnlySpanIdsKey {
        public final Span span;

        public OnlySpanIdsKey(Span span) {
            this.span = span;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Span)) {
                return false;
            }

            Span other = (Span) o;

            if (span.getTraceId() != null ? !span.getTraceId().equals(other.getTraceId()) : other.getTraceId() != null) {
                return false;
            }
            if (span.getSpanId() != null ? !span.getSpanId().equals(other.getSpanId()) : other.getSpanId() != null) {
                return false;
            }
            if (span.getParentId() != null ? !span.getParentId().equals(other.getParentId()) : other.getParentId() != null) {
                return false;
            }
            return span.getProcess() != null ? span.equals(other.getProcess()) : other.getProcess() == null;
        }

        @Override
        public int hashCode() {
            int result = span.getTraceId() != null ? span.getTraceId().hashCode() : 0;
            result = 31 * result + (span.getSpanId() != null ? span.getSpanId().hashCode() : 0);
            result = 31 * result + (span.getParentId() != null ? span.getParentId().hashCode() : 0);
            result = 31 * result + (span.getProcess() != null ? span.getProcess().hashCode() : 0);
            return result;
        }
    }
}
