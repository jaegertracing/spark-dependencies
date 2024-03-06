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

import brave.Tracing;
import brave.sampler.Sampler;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.JaegerTracer.Builder;
import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.reporters.RemoteReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import io.jaegertracing.thrift.internal.senders.HttpSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.transport.TTransportException;
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
      return new JaegerWrapper((io.jaegertracing.internal.JaegerTracer)tracer);
    }

    public Flushable flushable() {
      return flushable;
    }
  }

  public static List<TracerHolder<JaegerTracer>> generateJaeger(int number, String collectorUrl) throws TTransportException {
    List<TracerHolder<JaegerTracer>> tracers = new ArrayList<>(number);
    for (int i = 0; i < number; i++) {
      String serviceName = serviceName();
      Tuple<JaegerTracer, Flushable> jaegerTracer = createJaeger(serviceName, collectorUrl);
      tracers.add(new TracerHolder<>(jaegerTracer.getA(), serviceName, jaegerTracer.getB()));
    }
    return tracers;
  }

  public static Tuple<JaegerTracer, Flushable> createJaeger(String serviceName, String collectorUrl) throws TTransportException {
    HttpSender sender = new HttpSender.Builder(collectorUrl + "/api/traces").build();
    RemoteReporter reporter = new RemoteReporter.Builder()
        .withSender(sender)
        .withMaxQueueSize(100000)
        .withFlushInterval(1)
        .build();
    return new Tuple<>(new Builder(serviceName)
        .withReporter(reporter)
        .withSampler(new ConstSampler(true))
        .build(),
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
        .closeTimeout(1, TimeUnit.MILLISECONDS)
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
