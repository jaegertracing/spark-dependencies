/**
 * Copyright 2019 The Jaeger Authors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.KeyValue;
import io.jaegertracing.spark.dependencies.model.Process;
import io.jaegertracing.spark.dependencies.model.Span;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public class SpansToDependencyLinksTest {

    @Test
    public void shouldReturnDependencyWithClientAndServerSpans() {
        SpansToDependencyLinks spansToDependencyLinks = new SpansToDependencyLinks("");
        Set<Span> sharedSpans = new HashSet<>();
        sharedSpans.add(createSpan("clientName", Tags.SPAN_KIND_CLIENT));
        sharedSpans.add(createSpan("serverName", Tags.SPAN_KIND_SERVER));
        Optional<Dependency> result = spansToDependencyLinks.sharedSpanDependency(sharedSpans);
        assertTrue(result.isPresent());
        assertEquals(new Dependency("clientName", "serverName"), result.get());
    }

    @Test
    public void shouldReturnDependencyWithConsumerAndProducer() {
        SpansToDependencyLinks spansToDependencyLinks = new SpansToDependencyLinks("");
        Set<Span> sharedSpans = new HashSet<>();
        sharedSpans.add(createSpan("consumerName", Tags.SPAN_KIND_CONSUMER));
        sharedSpans.add(createSpan("producerName", Tags.SPAN_KIND_PRODUCER));
        Optional<Dependency> result = spansToDependencyLinks.sharedSpanDependency(sharedSpans);
        assertTrue(result.isPresent());
        assertEquals(new Dependency("consumerName", "producerName"), result.get());
    }

    @Test
    public void shouldReturnEmptyDependencyForSpansWithoutSpanKindDefinition() {
        SpansToDependencyLinks spansToDependencyLinks = new SpansToDependencyLinks("");
        Set<Span> sharedSpans = new HashSet<>();
        sharedSpans.add(createSpan("consumerName", "tag"));
        sharedSpans.add(createSpan("producerName", "tag"));
        Optional<Dependency> result = spansToDependencyLinks.sharedSpanDependency(sharedSpans);
        assertFalse(result.isPresent());
    }

    private Span createSpan(String serviceName, String tag) {
        List<KeyValue> tags = new ArrayList<>();
        KeyValue keyValue = new KeyValue();
        keyValue.setKey("span.kind");
        keyValue.setValueString(tag);
        tags.add(keyValue);
        Span span = new Span();
        Process process = new Process();
        process.setServiceName(serviceName);
        span.setProcess(process);
        span.setTags(tags);
        return span;
    }
}
