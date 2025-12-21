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
