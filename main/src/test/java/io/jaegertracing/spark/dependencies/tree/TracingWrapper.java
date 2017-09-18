package io.jaegertracing.spark.dependencies.tree;

import brave.Span.Kind;
import brave.Tracing;
import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;

/**
 * @author Pavol Loffay
 */
public interface TracingWrapper<T> {
  T get();
  String serviceName();
  String operationName();
  void createChildSpan(TracingWrapper<T> parent);

  class JaegerWrapper implements TracingWrapper<JaegerWrapper> {

    private Tracer tracer;
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
      io.opentracing.Tracer.SpanBuilder spanBuilder = tracer.buildSpan(parent == null ? "|" : parent.get().operationName() + "->");
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
      if (parent == null) {
        operationName = "|";
        span = tracing.tracer().newTrace().name(operationName)
            .kind(Kind.CLIENT)
            .start();
      } else {
        operationName = parent.get().operationName() + "->";
        brave.Span spanJoinedServer = tracing.tracer().joinSpan(parent.get().span.context())
            .kind(Kind.SERVER)
            .name(operationName)
            .start();
        span = tracing.tracer().newChild(spanJoinedServer.context())
            .kind(Kind.CLIENT)
            .start();
        // do not finish it creates internal dependency link
//        spanJoinedServer.finish();
      }
    }

    public brave.Span getSpan() {
      return span;
    }
  }
}
