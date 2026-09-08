/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class OpenSearchDependenciesJobTest extends DependenciesTest {

  protected OpenSearchDependenciesJob dependenciesJob;
  protected final LocalDate testDay = LocalDate.now();
  static JaegerOpenSearchEnvironment jaegerOpenSearchEnvironment;

  @BeforeClass
  public static void beforeClass() {
    jaegerOpenSearchEnvironment = new JaegerOpenSearchEnvironment();
    jaegerOpenSearchEnvironment.start(new HashMap<>(), jaegerVersion(),
        JaegerOpenSearchEnvironment.opensearchVersion());
    collectorUrl = jaegerOpenSearchEnvironment.getCollectorUrl();
    queryUrl = jaegerOpenSearchEnvironment.getQueryUrl();
  }

  @Before
  public void before() throws Exception {
    String serviceName = UUID.randomUUID().toString();
    String operationName = UUID.randomUUID().toString();
    TracersGenerator.Tuple<Tracer, TracersGenerator.Flushable> tuple = TracersGenerator.createJaeger(serviceName,
        collectorUrl);
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
      jaegerOpenSearchEnvironment.cleanUp(dependenciesJob.indexDate("jaeger-span"),
          dependenciesJob.indexDate("jaeger-dependencies"));
    }
  }

  @AfterClass
  public static void afterClass() {
    jaegerOpenSearchEnvironment.stop();
  }

  @Test
  public void shouldReplaceDailySnapshotWhenJobRunsTwice() throws Exception {
    TracersGenerator.Tuple<Tracer, TracersGenerator.Flushable> parentTuple =
        TracersGenerator.createJaeger("snapshot-parent", collectorUrl);
    TracersGenerator.Tuple<Tracer, TracersGenerator.Flushable> childTuple =
        TracersGenerator.createJaeger("snapshot-child", collectorUrl);

    Span parent = parentTuple.getA().spanBuilder("parent").startSpan();
    Span child = childTuple.getA().spanBuilder("child")
        .setParent(io.opentelemetry.context.Context.current().with(parent))
        .startSpan();
    child.end();
    parent.end();
    parentTuple.getB().flush();
    childTuple.getB().flush();

    waitJaegerQueryContains("snapshot-parent", "parent");
    waitJaegerQueryContains("snapshot-child", "child");

    deriveDependencies();

    Map<String, Map<String, Long>> expectedDependencies = new HashMap<>();
    Map<String, Long> children = new HashMap<>();
    children.put("snapshot-child", 1L);
    expectedDependencies.put("snapshot-parent", children);
    assertDependencies(expectedDependencies);

    deriveDependencies();
    assertDependencies(expectedDependencies);
  }

  @Override
  protected void deriveDependencies() {
    dependenciesJob = OpenSearchDependenciesJob.builder()
        .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
        .day(testDay)
        .build();
    try {
      jaegerOpenSearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh OpenSearch", e);
    }
    dependenciesJob.run("peer.service");
    try {
      jaegerOpenSearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh OpenSearch", e);
    }
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    try {
      jaegerOpenSearchEnvironment.refresh();
    } catch (IOException e) {
      throw new RuntimeException("Could not refresh OpenSearch", e);
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
