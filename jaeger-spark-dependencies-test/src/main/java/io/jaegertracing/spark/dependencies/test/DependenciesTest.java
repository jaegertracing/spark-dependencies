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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.jaegertracing.spark.dependencies.test.TracersGenerator.Flushable;
import io.jaegertracing.spark.dependencies.test.TracersGenerator.Tuple;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.OpenTelemetryWrapper;
import io.jaegertracing.spark.dependencies.test.tree.Traversals;
import io.jaegertracing.spark.dependencies.test.tree.TreeGenerator;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public abstract class DependenciesTest {

  protected OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  protected ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  /**
   * Set these in subclasses
   */
  protected static String queryUrl;
  protected static String collectorUrl;

  public static String jaegerVersion() {
    String jaegerVersion = System.getProperty("jaeger.version", System.getenv("JAEGER_VERSION"));
    return jaegerVersion != null ? jaegerVersion : "2.13.0";
  }

  /**
   * Override this and run spark job
   */
  protected abstract void deriveDependencies() throws Exception;

  /**
   * Wait between submitting different traces
   */
  protected abstract void waitBetweenTraces() throws InterruptedException;

  @Test
  public void testJaegerOneTrace() throws Exception {
    System.out.println("=== Starting testJaegerOneTrace ===");
    System.out.println("Generating Jaeger trace tree with 5 tracers, 50 nodes, depth 3...");
    TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
        TracersGenerator.generateJaeger(5, collectorUrl));
    Node<OpenTelemetryWrapper> root = treeGenerator.generateTree(50, 3);
    System.out.println("Trace tree generated. Root service: " + root.getServiceName() + ", operation: " + root.getTracingWrapper().operationName());
    
    System.out.println("Finishing spans...");
    Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().end());
    waitBetweenTraces();
    
    System.out.println("Flushing and closing tracers...");
    treeGenerator.getTracers().forEach(tracer -> {
      tracer.flushable().flush();
    });
    
    // Give extra time for spans to be exported and indexed
    TimeUnit.SECONDS.sleep(2);
    
    System.out.println("Waiting for traces to appear in Jaeger Query...");
    waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    System.out.println("Traces found in Jaeger Query");

    System.out.println("Deriving dependencies...");
    deriveDependencies();
    System.out.println("Dependencies derived, asserting results...");
    assertDependencies(DependencyLinkDerivator.serviceDependencies(root));
    System.out.println("=== testJaegerOneTrace completed successfully ===");
  }

  @Test
  public void testJaegerMultipleTraces() throws Exception {
    System.out.println("=== Starting testJaegerMultipleTraces ===");
    System.out.println("Generating 20 Jaeger trace trees with 50 tracers each...");
    TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
        TracersGenerator.generateJaeger(50, collectorUrl));
    Map<String, Map<String, Long>> expectedDependencies = new LinkedHashMap<>();
    for (int i = 0; i < 20; i++) {
      System.out.println("Generating trace " + (i + 1) + "/20...");
      Node<OpenTelemetryWrapper> root = treeGenerator.generateTree(50, 15);
      DependencyLinkDerivator.serviceDependencies(root, expectedDependencies);
      Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().end());
      waitBetweenTraces();
      waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    }
    System.out.println("All 20 traces generated and verified");
    
    System.out.println("Flushing and closing tracers...");
    // flush and wait for reported data
    treeGenerator.getTracers().forEach(tracer -> tracer.flushable().flush());
    
    // Give extra time for spans to be exported and indexed
    TimeUnit.SECONDS.sleep(2);

    System.out.println("Deriving dependencies...");
    deriveDependencies();
    System.out.println("Dependencies derived, asserting results...");
    assertDependencies(expectedDependencies);
    System.out.println("=== testJaegerMultipleTraces completed successfully ===");
  }



  @Test
  public void testMultipleReferences() throws Exception {
    System.out.println("=== Starting testMultipleReferences ===");
    System.out.println("Creating tracers for services S1, S2, S3...");
    Tuple<Tracer, Flushable> s1Tuple = TracersGenerator.createJaeger("S1", collectorUrl);
    Tuple<Tracer, Flushable> s2Tuple = TracersGenerator.createJaeger("S2", collectorUrl);
    Tuple<Tracer, Flushable> s3Tuple = TracersGenerator.createJaeger("S3", collectorUrl);

    System.out.println("Creating spans with multiple references...");
    // Note: OpenTelemetry doesn't support FOLLOWS_FROM references like OpenTracing did.
    // In OpenTelemetry, a span can only have one parent. Both s2Span and s3Span will have
    // s1Span as their parent, creating S1->S2 and S1->S3 dependencies.
    Span s1Span = s1Tuple.getA().spanBuilder("foo").startSpan();
    Span s2Span = s2Tuple.getA().spanBuilder("bar")
        .setParent(io.opentelemetry.context.Context.current().with(s1Span))
        .startSpan();
    Span s3Span = s3Tuple.getA().spanBuilder("baz")
        .setParent(io.opentelemetry.context.Context.current().with(s1Span))
        .startSpan();

    System.out.println("Finishing and flushing spans...");
    s1Span.end();
    s2Span.end();
    s3Span.end();
    s1Tuple.getB().flush();
    s2Tuple.getB().flush();
    s3Tuple.getB().flush();
    
    // Give extra time for spans to be exported and indexed
    TimeUnit.SECONDS.sleep(2);
    
    System.out.println("Waiting for traces to appear in Jaeger Query...");
    waitJaegerQueryContains("S1", "foo");
    waitJaegerQueryContains("S2", "bar");
    waitJaegerQueryContains("S3", "baz");
    System.out.println("All traces found in Jaeger Query");

    System.out.println("Deriving dependencies...");
    deriveDependencies();

    Map<String, Map<String, Long>> expectedDependencies = new HashMap<>();
    Map<String, Long> s1Descendants = new HashMap<>();
    s1Descendants.put("S2", 1L);
    s1Descendants.put("S3", 1L);
    expectedDependencies.put("S1", s1Descendants);
    System.out.println("Dependencies derived, asserting results...");
    assertDependencies(expectedDependencies);
    System.out.println("=== testMultipleReferences completed successfully ===");
  }

  protected void assertDependencies(Map<String, Map<String, Long>> expectedDependencies) throws IOException {
    Request request = new Request.Builder()
        .url(queryUrl + "/api/dependencies?endTs=" + System.currentTimeMillis())
        .get()
        .build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      RestResult<DependencyLink> restResult = objectMapper.readValue(response.body().string(), new TypeReference<RestResult<DependencyLink>>() {});
      assertEquals(null, restResult.getErrors());
      assertEquals(expectedDependencies, DependencyLinkDerivator.serviceDependencies(restResult.getData()));
    }
  }

  protected void waitJaegerQueryContains(String service, String spanContainsThis) {
    String url = String.format("%s/api/traces?service=%s", queryUrl, service);
    System.out.println("Waiting for trace in Jaeger Query. Service: " + service + ", looking for: " + spanContainsThis);
    System.out.println("Query URL: " + url);
    
    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();
    await()
        .pollInterval(1, TimeUnit.SECONDS)
        .atMost(30, TimeUnit.SECONDS)
        .until(() -> {
      try(Response response = okHttpClient.newCall(request).execute()) {
        String responseBody = response.body().string();
        int statusCode = response.code();
        boolean contains = responseBody.contains(spanContainsThis);
        
        if (!contains) {
          // Log the response when condition is not met to help with debugging
          System.out.println("Trace not found yet. Status code: " + statusCode);
          System.out.println("Response body preview (first 500 chars): " + 
              (responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody));
        }
        
        return contains;
      }
    });
    System.out.println("Trace found for service: " + service);
  }
}
