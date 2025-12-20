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

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerTracer;
import java.util.UUID;

/**
 * Encapsulates tracing information about one node(service) in the graph.
 * It allows to create one span for this node. Caller is responsible to
 * call {@link #createChildSpan(TracingWrapper)} and finish the span.
 *
 * @author Pavol Loffay
 */
public interface TracingWrapper<T extends TracingWrapper> {
  T get();
  String serviceName();
  String operationName();
  void createChildSpan(TracingWrapper<T> parent);

  class JaegerWrapper implements TracingWrapper<JaegerWrapper> {
    private final JaegerTracer tracer;
    private JaegerSpan span;

    public JaegerWrapper(JaegerTracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public JaegerWrapper get() {
      return this;
    }

    @Override
    public String serviceName() {
      return tracer.getServiceName();
    }

    @Override
    public String operationName() {
      return span.getOperationName();
    }

    @Override
    public void createChildSpan(TracingWrapper<JaegerWrapper> parent) {
      io.opentracing.Tracer.SpanBuilder spanBuilder = tracer.buildSpan(UUID.randomUUID().toString().replace("-", ""));
      if (parent != null) {
        spanBuilder.asChildOf(parent.get().span);
      }
      span = (JaegerSpan)spanBuilder.startManual();
    }

    public JaegerSpan getSpan() {
      return span;
    }
  }
}
