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

import brave.Tracing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.Tracer;
import io.jaegertracing.spark.dependencies.test.TracersGenerator.Flushable;
import io.jaegertracing.spark.dependencies.test.TracersGenerator.Tuple;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import io.jaegertracing.spark.dependencies.test.tree.Traversals;
import io.jaegertracing.spark.dependencies.test.tree.TreeGenerator;
import io.opentracing.References;
import io.opentracing.Span;
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
  protected static String zipkinCollectorUrl;

  public static String jaegerVersion() {
    String jaegerVersion = System.getProperty("jaeger.version", System.getenv("JAEGER_VERSION"));
    return jaegerVersion != null ? jaegerVersion : "latest";
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
    TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
        TracersGenerator.generateJaeger(5, collectorUrl));
    Node<JaegerWrapper> root = treeGenerator.generateTree(50, 3);
    Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
    waitBetweenTraces();
    // TODO move to TracersGenerator once jaeger tracer implements closeable.
    treeGenerator.getTracers().forEach(tracer -> {
      tracer.getTracer().close();
    });
    waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());

    deriveDependencies();
    assertDependencies(DependencyLinkDerivator.serviceDependencies(root));
  }

  @Test
  public void testJaegerMultipleTraces() throws Exception {
    TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
        TracersGenerator.generateJaeger(50, collectorUrl));
    Map<String, Map<String, Long>> expectedDependencies = new LinkedHashMap<>();
    for (int i = 0; i < 20; i++) {
      Node<JaegerWrapper> root = treeGenerator.generateTree(50, 15);
      DependencyLinkDerivator.serviceDependencies(root, expectedDependencies);
      Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
      waitBetweenTraces();
      waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    }
    // flush and wait for reported data
    treeGenerator.getTracers().forEach(tracer -> tracer.getTracer().close());

    deriveDependencies();
    assertDependencies(expectedDependencies);
  }

  @Test
  public void testZipkinOneTraceFixed6NodesTwoTracers() throws Exception {
    Tuple<Tracing, Flushable> rootTuple = TracersGenerator.createZipkin("root", zipkinCollectorUrl);
    Tuple<Tracing, Flushable> tracer2 = TracersGenerator.createZipkin("tracer2", zipkinCollectorUrl);

    Node<ZipkinWrapper> root = new Node<>(new ZipkinWrapper(rootTuple.getA(), "root"), null);
    Node<ZipkinWrapper> child11 = new Node<>(new ZipkinWrapper(tracer2.getA(), "tracer2"), root);
    new Node<>(new ZipkinWrapper(tracer2.getA(), "tracer2"), root);
    new Node<>(new ZipkinWrapper(tracer2.getA(), "tracer2"), root);

    new Node<>(new ZipkinWrapper(tracer2.getA(), "tracer2"), child11);
    new Node<>(new ZipkinWrapper(tracer2.getA(), "tracer2"), child11);


    Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
    rootTuple.getA().close();
    tracer2.getA().close();
    waitBetweenTraces();

    waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    deriveDependencies();
    assertDependencies(DependencyLinkDerivator.serviceDependencies(root));
  }

  @Test
  public void testZipkinOneTrace() throws Exception {
    TreeGenerator<Tracing> treeGenerator = new TreeGenerator(TracersGenerator.generateZipkin(2, zipkinCollectorUrl));
    Node<ZipkinWrapper> root = treeGenerator.generateTree(50, 3);
    Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
    waitBetweenTraces();
    treeGenerator.getTracers().forEach(tracer -> {
      tracer.getTracer().close();
      // tracer.close does not seem to flush all data
      tracer.flushable().flush();
    });

    waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    deriveDependencies();
    assertDependencies(DependencyLinkDerivator.serviceDependencies(root));
  }

  @Test
  public void testZipkinMultipleTraces() throws Exception {
    TreeGenerator<Tracing> treeGenerator = new TreeGenerator(TracersGenerator.generateZipkin(5, zipkinCollectorUrl));
    Map<String, Map<String, Long>> expectedDependencies = new LinkedHashMap<>();
    for (int i = 0; i < 20; i++) {
      Node<ZipkinWrapper> root = treeGenerator.generateTree(50, 3);
      DependencyLinkDerivator.serviceDependencies(root, expectedDependencies);
      Traversals.postOrder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
      waitBetweenTraces();
      waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    }
    treeGenerator.getTracers().forEach(tracer -> {
      tracer.getTracer().close();
      tracer.flushable().flush();
    });

    deriveDependencies();
    assertDependencies(expectedDependencies);
  }

  @Test
  public void testMultipleReferences() throws Exception {
    Tuple<Tracer, Flushable> s1Tuple = TracersGenerator.createJaeger("S1", collectorUrl);
    Tuple<Tracer, Flushable> s2Tuple = TracersGenerator.createJaeger("S2", collectorUrl);
    Tuple<Tracer, Flushable> s3Tuple = TracersGenerator.createJaeger("S3", collectorUrl);

    Span s1Span = s1Tuple.getA().buildSpan("foo")
        .ignoreActiveSpan()
        .start();
    Span s2Span = s2Tuple.getA().buildSpan("bar")
        .addReference(References.CHILD_OF, s1Span.context())
        .start();
    Span s3Span = s3Tuple.getA().buildSpan("baz")
        .addReference(References.CHILD_OF, s1Span.context())
        .addReference(References.FOLLOWS_FROM, s2Span.context())
        .start();

    s1Span.finish();
    s2Span.finish();
    s3Span.finish();
    s1Tuple.getB().flush();
    s2Tuple.getB().flush();
    s3Tuple.getB().flush();
    waitJaegerQueryContains("S1", "foo");
    waitJaegerQueryContains("S2", "bar");
    waitJaegerQueryContains("S3", "baz");

    deriveDependencies();

    Map<String, Map<String, Long>> expectedDependencies = new HashMap<>();
    Map<String, Long> s1Descendants = new HashMap<>();
    s1Descendants.put("S2", 1L);
    s1Descendants.put("S3", 1L);
    expectedDependencies.put("S1", s1Descendants);
    Map<String, Long> s2Descendants = new HashMap<>();
    s2Descendants.put("S3", 1L);
    expectedDependencies.put("S2", s2Descendants);
    assertDependencies(expectedDependencies);
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
    Request request = new Request.Builder()
        .url(String.format("%s/api/traces?service=%s", queryUrl, service))
        .get()
        .build();
    await().atMost(30, TimeUnit.SECONDS).until(() -> {
      Response response = okHttpClient.newCall(request).execute();
      return response.body().string().contains(spanContainsThis);
    });
  }
}
