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
package io.jaegertracing.spark.dependencies.test;

import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.mock.MockTracer.SpanBuilder;

/**
 * @author Pavol Loffay
 */
public class MockTracingWrapper implements TracingWrapper<MockTracingWrapper> {

  private String serviceName;
  private MockTracer tracer;
  private MockSpan span;

  public MockTracingWrapper(MockTracer mockTracer, String serviceName) {
    this.serviceName = serviceName;
    this.tracer = mockTracer;
  }

  @Override
  public MockTracingWrapper get() {
    return this;
  }

  @Override
  public String serviceName() {
    return serviceName;
  }

  @Override
  public String operationName() {
    return span != null ? span.operationName() : null;
  }

  @Override
  public void createChildSpan(TracingWrapper<MockTracingWrapper> parent) {
    SpanBuilder spanBuilder = tracer.buildSpan(parent == null ? "|" : parent.get().operationName() + "->");
    if (parent != null) {
      spanBuilder.asChildOf(parent.get().span);
    }
    span = spanBuilder.startManual();
  }
}
