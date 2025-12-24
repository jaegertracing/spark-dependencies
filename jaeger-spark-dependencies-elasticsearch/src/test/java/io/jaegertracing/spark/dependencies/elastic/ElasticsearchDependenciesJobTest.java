/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;


import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

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
    queryUrl = jaegerElasticsearchEnvironment.getQueryUrl();
  }

  @Before
  public void before() {
    String serviceName = UUID.randomUUID().toString();
    String operationName = UUID.randomUUID().toString();
    TracersGenerator.Tuple<Tracer, TracersGenerator.Flushable> tuple = TracersGenerator.createJaeger(serviceName, collectorUrl);
    Tracer initStorageTracer = tuple.getA();
    Span span = initStorageTracer.spanBuilder(operationName).startSpan();
    span.setAttribute("foo", "bar");
    span.end();
    tuple.getB().flush();
    try {
      // Give extra time for spans to be exported and indexed
      TimeUnit.SECONDS.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    waitJaegerQueryContains(serviceName, "foo");
  }

  @After
  public void after() throws IOException {
    if (dependenciesJob != null) {
      jaegerElasticsearchEnvironment.cleanUp(dependenciesJob.indexDate("jaeger-span"), dependenciesJob.indexDate("jaeger-dependencies"));
    }
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
