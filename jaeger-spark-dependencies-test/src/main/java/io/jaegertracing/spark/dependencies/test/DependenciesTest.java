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
import com.uber.jaeger.Tracer;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import io.jaegertracing.spark.dependencies.test.tree.Traversals;
import io.jaegertracing.spark.dependencies.test.tree.TreeGenerator;
import java.io.IOException;
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
    Node<JaegerWrapper> root = treeGenerator.generateTree(200, 3);
    Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
    waitBetweenTraces();
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
      Node<JaegerWrapper> root = treeGenerator.generateTree(150, 15);
      DependencyLinkDerivator.serviceDependencies(root, expectedDependencies);
      Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
      waitBetweenTraces();
      waitJaegerQueryContains(root.getServiceName(), root.getTracingWrapper().operationName());
    }
    // flush and wait for reported data
    treeGenerator.getTracers().forEach(tracer -> tracer.getTracer().close());

    deriveDependencies();
    assertDependencies(expectedDependencies);
  }

  @Test
  public void testZipkinOneTrace() throws Exception {
    TreeGenerator<Tracing> treeGenerator = new TreeGenerator(TracersGenerator.generateZipkin(5, zipkinCollectorUrl));
    Node<ZipkinWrapper> root = treeGenerator.generateTree(150, 3);
    Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
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
      Node<ZipkinWrapper> root = treeGenerator.generateTree(150, 3);
      DependencyLinkDerivator.serviceDependencies(root, expectedDependencies);
      Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
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
      String body = response.body().string();
      return body.contains(spanContainsThis);
    });
  }
}
