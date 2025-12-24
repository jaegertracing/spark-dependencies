/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
    // Parse collectorUrl to extract host and port for OTLP gRPC
    // collectorUrl is in format "http://host:port"
    String host = "localhost";
    int port = 4317; // default
    
    try {
      // Parse the URL to extract host and port
      String urlStr = collectorUrl;
      // Remove scheme
      if (urlStr.startsWith("http://")) {
        urlStr = urlStr.substring(7);
      } else if (urlStr.startsWith("https://")) {
        urlStr = urlStr.substring(8);
      }
      // Remove path if present
      int slashIndex = urlStr.indexOf('/');
      if (slashIndex > 0) {
        urlStr = urlStr.substring(0, slashIndex);
      }
      // Extract host and port
      int colonIndex = urlStr.lastIndexOf(':');
      if (colonIndex > 0) {
        host = urlStr.substring(0, colonIndex);
        port = Integer.parseInt(urlStr.substring(colonIndex + 1));
      } else {
        host = urlStr;
      }
    } catch (Exception e) {
      System.err.println("[ERROR TracersGenerator] Failed to parse collectorUrl: " + collectorUrl + ", error: " + e.getMessage());
    }
    
    // Reconstruct endpoint in the format expected by gRPC exporter
    String otlpEndpoint = "http://" + host + ":" + port;

    Resource resource = Resource.getDefault()
        .merge(Resource.builder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .build());

    // For gRPC, the endpoint should include the scheme (http:// or https://)
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
            // Force flush to ensure all spans are exported
            sdkTracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
            // Shutdown to ensure proper cleanup
            sdkTracerProvider.shutdown().join(10, TimeUnit.SECONDS);
          } catch (Exception ex) {
            throw new IllegalStateException("Failed to flush and shutdown tracer provider", ex);
          }
        });
  }

  private static String serviceName() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
