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
package io.jaegertracing.spark.dependencies.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SpanTest {

    @Test
    public void testEquals() {
        Span span1 = new Span();
        span1.setTraceId("trace1");
        span1.setSpanId(1L);
        span1.setProcess(createProcess("service1"));

        Span span2 = new Span();
        span2.setTraceId("trace1");
        span2.setSpanId(1L);
        span2.setProcess(createProcess("service1"));

        assertEquals(span1, span2);

        // Different service name
        Span span3 = new Span();
        span3.setTraceId("trace1");
        span3.setSpanId(1L);
        span3.setProcess(createProcess("service2"));

        assertNotEquals(span1, span3);

        // Different tags
        List<KeyValue> tags1 = new ArrayList<>();
        KeyValue kv1 = new KeyValue();
        kv1.setKey("key");
        kv1.setValueString("value1");
        tags1.add(kv1);
        span1.setTags(tags1);

        List<KeyValue> tags2 = new ArrayList<>();
        KeyValue kv2 = new KeyValue();
        kv2.setKey("key");
        kv2.setValueString("value1");
        tags2.add(kv2);
        span2.setTags(tags2);

        assertEquals(span1, span2);

        tags2.get(0).setValueString("value2");
        assertNotEquals(span1, span2);
    }

    private Process createProcess(String serviceName) {
        Process process = new Process();
        process.setServiceName(serviceName);
        return process;
    }
}
