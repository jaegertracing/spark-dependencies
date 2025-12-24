/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.test.tree;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.UUID;

/**
 * Encapsulates tracing information about one node(service) in the graph.
 * It allows to create one span for this node. Caller is responsible to
 * call {@link #createChildSpan(TracingWrapper)} and finish the span. The
 * parent parameter in createChildSpan should be of the same type as the
 * implementing wrapper.
 *
 * @author Pavol Loffay
 */
public interface TracingWrapper<T extends TracingWrapper> {
  T get();
  String serviceName();
  String operationName();
  void createChildSpan(TracingWrapper<T> parent);

  class OpenTelemetryWrapper implements TracingWrapper<OpenTelemetryWrapper> {
    private final Tracer tracer;
    private final String serviceName;
    private Span span;
    private String operationName;

    public OpenTelemetryWrapper(Tracer tracer, String serviceName) {
      this.tracer = tracer;
      this.serviceName = serviceName;
    }

    @Override
    public OpenTelemetryWrapper get() {
      return this;
    }

    @Override
    public String serviceName() {
      return serviceName;
    }

    @Override
    public String operationName() {
      return operationName;
    }

    @Override
    public void createChildSpan(TracingWrapper<OpenTelemetryWrapper> parent) {
      operationName = UUID.randomUUID().toString().replace("-", "");
      
      if (parent != null && parent.get().span != null) {
        Context parentContext = Context.current().with(parent.get().span);
        span = tracer.spanBuilder(operationName)
            .setParent(parentContext)
            .startSpan();
      } else {
        span = tracer.spanBuilder(operationName)
            .setNoParent()
            .startSpan();
      }
    }

    public Span getSpan() {
      return span;
    }
  }
}
