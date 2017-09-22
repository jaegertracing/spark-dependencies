package io.jaegertracing.spark.dependencies.test;

import brave.Tracing;
import brave.sampler.Sampler;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.Tracer.Builder;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;
import zipkin.reporter.okhttp3.OkHttpSender;

/**
 * @author Pavol Loffay
 */
public class TracersGenerator {

  public static class Tuple<A, B> {
    private final A a;
    private final B b;

    Tuple(A a, B b) {
      this.a = a;
      this.b = b;
    }

    public A getA() {
      return a;
    }

    public B getB() {
      return b;
    }
  }

  public interface Flushable {
    void flush();
  }

  public static class TracerHolder<T> {
    private T tracer;
    private String serviceName;
    private Flushable flushable;

    TracerHolder(T tracer, String serviceName, Flushable flushable) {
      this.tracer = tracer;
      this.serviceName = serviceName;
      this.flushable = flushable;
    }

    public T getTracer() {
      return tracer;
    }

    public TracingWrapper tracingWrapper() {
      if (tracer instanceof Tracing) {
        return new ZipkinWrapper((brave.Tracing)tracer, serviceName);
      }
      return new JaegerWrapper((com.uber.jaeger.Tracer)tracer);
    }

    public Flushable flushable() {
      return flushable;
    }
  }

  public static List<TracerHolder<Tracer>> generateJaeger(int number, String collectorUrl) {
    List<TracerHolder<Tracer>> tracers = new ArrayList<>(number);
    for (int i = 0; i < number; i++) {
      String serviceName = serviceName();
      Tuple<Tracer, Flushable> jaegerTracer = createJaeger(serviceName, collectorUrl);
      tracers.add(new TracerHolder<>(jaegerTracer.getA(), serviceName, jaegerTracer.getB()));
    }
    return tracers;
  }

  public static Tuple<Tracer, Flushable> createJaeger(String serviceName, String collectorUrl) {
    HttpSender sender = new HttpSender(collectorUrl + "/api/traces", 65000);
    RemoteReporter reporter = new RemoteReporter(sender, 1, 100000,
        new Metrics(new StatsFactoryImpl(new NullStatsReporter())));
    return new Tuple<>(new Builder(serviceName, reporter, new ConstSampler(true)).build(),
        () -> {
      try {
        sender.flush();
      } catch (SenderException ex) {
        throw new IllegalStateException("Failed to send", ex);
      }
    });
  }

  public static List<TracerHolder<Tracing>> generateZipkin(int number, String collectorUrl) {
    List<TracerHolder<Tracing>> tracers = new ArrayList<>(number);
    for (int i = 0; i < number; i++) {
      String serviceName = serviceName();
      Tuple<Tracing, Flushable> zipkinTracer = createZipkin(serviceName, collectorUrl);
      tracers.add(new TracerHolder<>(zipkinTracer.getA(), serviceName, zipkinTracer.getB()));
    }
    return tracers;
  }

  public static Tuple<Tracing, Flushable> createZipkin(String serviceName, String collectorUrl) {
    Sender sender = OkHttpSender.builder()
      .endpoint(collectorUrl + "/api/v1/spans")
      .encoding(Encoding.JSON)
      .build();

    AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
        // TODO when using this number of Spaks in Spark != reported spans
//        .queuedMaxSpans(100000)
        .build();
    return new Tuple<>(Tracing.newBuilder()
        .localServiceName(serviceName)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .traceId128Bit(true)
        .reporter(reporter)
        .build(), () -> reporter.flush());
  }

  private static String serviceName() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
