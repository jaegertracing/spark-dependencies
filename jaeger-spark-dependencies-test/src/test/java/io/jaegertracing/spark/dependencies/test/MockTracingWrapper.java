/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
    span = spanBuilder.start();
  }
}
