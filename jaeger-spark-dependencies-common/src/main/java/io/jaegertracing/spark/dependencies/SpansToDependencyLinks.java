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
