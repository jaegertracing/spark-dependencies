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

import brave.Span.Kind;
import brave.Tracing;
import io.jaegertracing.Span;
import io.jaegertracing.Tracer;
import java.util.UUID;

/**
 * Encapsulates tracing information about one node(service) in the graph.
 * It allows to create one span for this node. Caller is responsible to
 * call {@link #createChildSpan(TracingWrapper)} and finish the span. Node that different
 * implementation might create different spans (e.g. zipkin shared span model)
 *
 * @author Pavol Loffay
 */
public interface TracingWrapper<T extends TracingWrapper> {
  T get();
  String serviceName();
  String operationName();
  void createChildSpan(TracingWrapper<T> parent);

  class JaegerWrapper implements TracingWrapper<JaegerWrapper> {
    private final Tracer tracer;
    private Span span;

    public JaegerWrapper(Tracer tracer) {
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
      span = (Span)spanBuilder.startManual();
    }

    public Span getSpan() {
      return span;
    }
  }

  class ZipkinWrapper implements TracingWrapper<ZipkinWrapper> {
    private final Tracing tracing;
    private final String serviceName;
    private String operationName;
    private brave.Span span;

    public ZipkinWrapper(Tracing tracing, String serviceName) {
      this.tracing = tracing;
      this.serviceName = serviceName;
    }

    @Override
    public ZipkinWrapper get() {
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
    public void createChildSpan(TracingWrapper<ZipkinWrapper> parent) {
      operationName = UUID.randomUUID().toString().replace("-","");
      if (parent == null) {
        // root node we start a new trace
        span = tracing.tracer().newTrace().name(operationName + "-root")
           .start();
      } else {
        brave.Span parentClient = parent.get().tracing.tracer().newChild(parent.get().span.context())
            .kind(Kind.CLIENT)
            .name(operationName + "-client")
            .start();
        // TODO if I finish this later the span is cached
        // and joined with server span and reported as a single span.
        // to properly solve this we have to look into the tags.
        // However there is another problem jaeger adds only one span.kind
        // (even if span contains cs,cr,sr,ss)
        // And it filters out core annotations, so there is no way how to find out
        // that there is a dependency link in this span.
        // https://github.com/jaegertracing/jaeger/issues/451
        parentClient.finish();
        span = tracing.tracer().joinSpan(parentClient.context())
            .name(operationName + "-server")
            .kind(Kind.SERVER)
            .start();
      }
    }

    public brave.Span getSpan() {
      return span;
    }
  }
}
