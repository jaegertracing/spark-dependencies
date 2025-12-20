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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.OpenTelemetryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
      return new OpenTelemetryWrapper((Tracer)tracer, serviceName);
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
    // Parse the collector URL to extract host and port for OTLP gRPC endpoint
    // Extract the base URL (host:port)
    String otlpEndpoint = collectorUrl;
    int slashIndex = otlpEndpoint.indexOf('/', otlpEndpoint.indexOf("//") + 2);
    if (slashIndex > 0) {
      otlpEndpoint = otlpEndpoint.substring(0, slashIndex);
    }
    // Change to OTLP gRPC port (4317)
    otlpEndpoint = otlpEndpoint.replaceAll(":\\d+$", ":4317");

    Resource resource = Resource.getDefault()
        .merge(Resource.builder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .build());

    OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint(otlpEndpoint)
        .setTimeout(10, TimeUnit.SECONDS)
        .build();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(100000)
            .setScheduleDelay(1, TimeUnit.MILLISECONDS)
            .build())
        .setResource(resource)
        .build();

    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .build();

    Tracer tracer = openTelemetry.getTracer(serviceName);

    return new Tuple<>(tracer,
        () -> {
          try {
            sdkTracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
          } catch (Exception ex) {
            throw new IllegalStateException("Failed to flush", ex);
          }
        });
  }

  private static String serviceName() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
