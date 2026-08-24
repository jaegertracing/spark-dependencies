/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.KeyValue;
import io.jaegertracing.spark.dependencies.model.Process;
import io.jaegertracing.spark.dependencies.model.Reference;
import io.jaegertracing.spark.dependencies.model.Span;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertEquals(new Dependency("producerName", "consumerName"), result.get());
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

    @Test
    public void shouldCountDuplicateParentReferencesOnce() throws Exception {
        Span parent = spanWithId(1L, "S1");
        Span child = spanWithId(2L, "S2");
        Reference first = new Reference();
        first.setSpanId(1L);
        Reference duplicate = new Reference();
        duplicate.setSpanId(1L);
        child.setRefs(Arrays.asList(first, duplicate));

        List<Dependency> dependencies = new ArrayList<>();
        new SpansToDependencyLinks("").call(Arrays.asList(parent, child))
            .forEachRemaining(dependencies::add);

        assertEquals(1, dependencies.size());
        assertEquals(new Dependency("S1", "S2"), dependencies.get(0));
    }

    @Test
    public void shouldIgnoreZeroSpanIdRefsWhenDetectingLeaves() throws Exception {
        Span root = spanWithId(0L, "S1");
        KeyValue peer = new KeyValue();
        peer.setKey("peer.service");
        peer.setValueString("uninstrumented");
        root.getTags().add(peer);
        // Spans with no refs skip leaf detection entirely, so give the root a
        // non-matching parent ref so we still reach the children-map check.
        Reference missingParent = new Reference();
        missingParent.setSpanId(99L);
        root.setRefs(Arrays.asList(missingParent));

        Span child = spanWithId(2L, "S2");
        Reference zeroParent = new Reference();
        zeroParent.setSpanId(0L);
        child.setRefs(Arrays.asList(zeroParent));

        List<Dependency> dependencies = new ArrayList<>();
        new SpansToDependencyLinks("peer.service").call(Arrays.asList(root, child))
            .forEachRemaining(dependencies::add);

        assertEquals(1, dependencies.size());
        assertEquals(new Dependency("S1", "uninstrumented"), dependencies.get(0));
    }

    private Span spanWithId(long spanId, String serviceName) {
        Span span = new Span();
        span.setTraceId("trace");
        span.setSpanId(spanId);
        span.setTags(new ArrayList<>());
        span.setRefs(new ArrayList<>());
        Process process = new Process();
        process.setServiceName(serviceName);
        span.setProcess(process);
        return span;
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
