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
package io.jaegertracing.spark.dependencies.elastic;


import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.DependencyLinkDerivator;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import static org.junit.Assert.assertEquals;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJobTest extends DependenciesTest {

  protected ElasticsearchDependenciesJob dependenciesJob;
  static JaegerElasticsearchEnvironment jaegerElasticsearchEnvironment;

  @BeforeClass
  public static void beforeClass() {
    jaegerElasticsearchEnvironment = new JaegerElasticsearchEnvironment();
    jaegerElasticsearchEnvironment.start(new HashMap<>(), jaegerVersion(), JaegerElasticsearchEnvironment.elasticsearchVersion());
    collectorUrl = jaegerElasticsearchEnvironment.getCollectorUrl();
    zipkinCollectorUrl = jaegerElasticsearchEnvironment.getZipkinCollectorUrl();
    queryUrl = jaegerElasticsearchEnvironment.getQueryUrl();
  }

  @Before
  public void before() throws TTransportException {
    JaegerTracer initStorageTracer = TracersGenerator.createJaeger(UUID.randomUUID().toString(), collectorUrl).getA();
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitJaegerQueryContains(initStorageTracer.getServiceName(), "foo");
  }

  @After
  public void after() throws IOException {
    jaegerElasticsearchEnvironment.cleanUp(dependenciesJob.indexDate("jaeger-span"), dependenciesJob.indexDate("jaeger-dependencies"));
  }

  @AfterClass
  public static void afterClass() {
    jaegerElasticsearchEnvironment.stop();
  }

  @Override
  protected void deriveDependencies() {
    dependenciesJob = ElasticsearchDependenciesJob.builder()
        .nodes("http://" + jaegerElasticsearchEnvironment.getElasticsearchIPPort())
        .day(LocalDate.now())
        .build();
    try {
      jaegerElasticsearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh Elasticsearch", e);
    }
    dependenciesJob.run("peer.service");
    try {
      jaegerElasticsearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh Elasticsearch", e);
    }
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    try {
      jaegerElasticsearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh Elasticsearch", e);
    }
  }

  @Override
  protected void assertDependencies(Map<String, Map<String, Long>> expectedDependencies) throws IOException {
    // Filter out "jaeger" service from expected dependencies (Jaeger v2 internal telemetry)
    Map<String, Map<String, Long>> filteredExpected = new HashMap<>();
    for (Map.Entry<String, Map<String, Long>> entry : expectedDependencies.entrySet()) {
      if (!"jaeger".equals(entry.getKey())) {
        Map<String, Long> childServices = new HashMap<>(entry.getValue());
        childServices.remove("jaeger"); // Also remove jaeger as a child service
        if (!childServices.isEmpty() || entry.getValue().isEmpty()) {
          filteredExpected.put(entry.getKey(), childServices);
        }
      }
    }
    
    // Get actual dependencies and filter out "jaeger" service
    Request request = new Request.Builder()
        .url(queryUrl + "/api/dependencies?endTs=" + System.currentTimeMillis())
        .get()
        .build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      RestResult<DependencyLink> restResult = objectMapper.readValue(response.body().string(), new TypeReference<RestResult<DependencyLink>>() {});
      assertEquals(null, restResult.getErrors());
      
      Map<String, Map<String, Long>> actualDependencies = DependencyLinkDerivator.serviceDependencies(restResult.getData());
      Map<String, Map<String, Long>> filteredActual = new HashMap<>();
      for (Map.Entry<String, Map<String, Long>> entry : actualDependencies.entrySet()) {
        if (!"jaeger".equals(entry.getKey())) {
          Map<String, Long> childServices = new HashMap<>(entry.getValue());
          childServices.remove("jaeger"); // Also remove jaeger as a child service
          if (!childServices.isEmpty() || entry.getValue().isEmpty()) {
            filteredActual.put(entry.getKey(), childServices);
          }
        }
      }
      
      assertEquals(filteredExpected, filteredActual);
    }
  }

  public static class BoundPortHttpWaitStrategy extends HttpWaitStrategy {
    private final int port;

    public BoundPortHttpWaitStrategy(int port) {
      this.port = port;
    }

    @Override
    protected Set<Integer> getLivenessCheckPorts() {
      int mapptedPort = this.waitStrategyTarget.getMappedPort(port);
      return Collections.singleton(mapptedPort);
    }
  }
}
