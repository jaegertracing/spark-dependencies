/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensearch.spark.rdd.api.java.JavaOpenSearchSpark;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class OpenSearchDependenciesJobTest extends DependenciesTest {

  protected OpenSearchDependenciesJob dependenciesJob;
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

  @Override
  protected void deriveDependencies() {
    dependenciesJob = OpenSearchDependenciesJob.builder()
        .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
        .day(LocalDate.now(ZoneOffset.UTC))
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

  @Test
  public void testReadFilteredFieldsExcludesLogs() throws Exception {
    String serviceName = "verify-os-projection-" + UUID.randomUUID().toString();
    TracersGenerator.Tuple<Tracer, TracersGenerator.Flushable> tuple = TracersGenerator.createJaeger(serviceName, collectorUrl);
    Tracer tracer = tuple.getA();
    Span span = tracer.spanBuilder("test-operation").startSpan();
    span.setAttribute("peer.service", "backend");
    span.setAttribute("test.custom.tag", "custom-value");
    span.addEvent("exception-event-log-message");
    span.end();
    tuple.getB().flush();
    try {
      TimeUnit.SECONDS.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    waitJaegerQueryContains(serviceName, "backend");
    jaegerOpenSearchEnvironment.refresh();

    OpenSearchDependenciesJob job = OpenSearchDependenciesJob.builder()
        .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
        .day(LocalDate.now(ZoneOffset.UTC))
        .build();
    this.dependenciesJob = job;

    JavaSparkContext sc = new JavaSparkContext(job.getConf());
    try {
      String spanIndex = job.indexDate("jaeger-span")[0];
      String osQuery = "{\"match_all\": {}}";
      List<Tuple2<String, Map<String, Object>>> docs = JavaOpenSearchSpark.opensearchRDD(sc, spanIndex, osQuery).collect();
      Assert.assertFalse("Should find documents in index", docs.isEmpty());

      boolean found = false;
      for (Tuple2<String, Map<String, Object>> t : docs) {
        Map<String, Object> map = t._2();
        Object processObj = map.get("process");
        if (processObj instanceof Map) {
          Map<?, ?> procMap = (Map<?, ?>) processObj;
          if (serviceName.equals(procMap.get("serviceName"))) {
            found = true;
            Assert.assertTrue("Should include traceID", map.containsKey("traceID"));
            Assert.assertTrue("Should include spanID", map.containsKey("spanID"));
            Assert.assertTrue("Should include startTime", map.containsKey("startTime"));
            Assert.assertTrue("Should include process", map.containsKey("process"));
            Assert.assertFalse("Logs must be excluded by opensearch.read.field.include", map.containsKey("logs"));
          }
        }
      }
      Assert.assertTrue("Our indexed span should be present in results", found);
    } finally {
      sc.stop();
      this.dependenciesJob = null;
    }
  }

  @Test
  public void testFilteredAndUnfilteredProduceIdenticalDependencyLinks() throws Exception {
    String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String spanIndex = "jaeger-span-" + date;
    String depIndex = "jaeger-dependencies-" + date;

    StringBuilder bulkBody = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      String traceId = String.format("%032x", (i / 4) + 1);
      String spanId = String.format("%016x", i + 1);
      String parentSpanId = (i % 4 == 0) ? "" : String.format("%016x", i);
      String caller = "service-" + (i % 4);
      String callee = "service-" + ((i % 4) + 1);

      String spanDoc = "{"
          + "\"traceID\":\"" + traceId + "\","
          + "\"spanID\":\"" + spanId + "\","
          + (parentSpanId.isEmpty() ? "" : "\"parentSpanID\":\"" + parentSpanId + "\",")
          + "\"startTime\":1000000,"
          + "\"startTimeMillis\":" + System.currentTimeMillis() + ","
          + "\"process\":{\"serviceName\":\"" + caller + "\"},"
          + "\"tags\":[{\"key\":\"span.kind\",\"value\":\"client\"},{\"key\":\"peer.service\",\"value\":\"" + callee + "\"}],"
          + "\"tag\":{\"span.kind\":\"client\",\"peer.service\":\"" + callee + "\"},"
          + (parentSpanId.isEmpty() ? "" : "\"references\":[{\"refType\":\"CHILD_OF\",\"traceID\":\"" + traceId + "\",\"spanID\":\"" + parentSpanId + "\"}],")
          + "\"logs\":[{\"timestamp\":1000000,\"fields\":[{\"key\":\"message\",\"value\":\"log payload\"}]}],"
          + "\"unrelatedMetadata\":\"payload padding\""
          + "}\n";

      bulkBody.append("{\"index\":{\"_index\":\"").append(spanIndex).append("\"}}\n");
      bulkBody.append(spanDoc);
    }

    Request bulkRequest = new Request.Builder()
        .url(String.format("http://%s/_bulk?refresh=true", jaegerOpenSearchEnvironment.getOpenSearchIPPort()))
        .post(RequestBody.create(MediaType.parse("application/x-ndjson"), bulkBody.toString()))
        .build();

    try (Response response = okHttpClient.newCall(bulkRequest).execute()) {
      Assert.assertTrue("Bulk indexing should succeed", response.isSuccessful());
    }
    jaegerOpenSearchEnvironment.refresh();

    try {
      // 1. Run baseline without field filtering
      OpenSearchDependenciesJob baselineJob = OpenSearchDependenciesJob.builder()
          .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
          .day(LocalDate.now(ZoneOffset.UTC))
          .readFieldInclude(null)
          .build();
      this.dependenciesJob = baselineJob;
      baselineJob.run(new String[]{spanIndex}, new String[]{depIndex}, "peer.service");
      jaegerOpenSearchEnvironment.refresh();
      String baselineDeps = getDependenciesJson(depIndex);

      cleanIndex(depIndex);
      jaegerOpenSearchEnvironment.refresh();

      // 2. Run with default field filtering
      OpenSearchDependenciesJob filteredJob = OpenSearchDependenciesJob.builder()
          .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
          .day(LocalDate.now(ZoneOffset.UTC))
          .build();
      this.dependenciesJob = filteredJob;
      filteredJob.run(new String[]{spanIndex}, new String[]{depIndex}, "peer.service");
      jaegerOpenSearchEnvironment.refresh();
      String filteredDeps = getDependenciesJson(depIndex);

      Assert.assertFalse("Dependency links should not be empty", baselineDeps.isEmpty());
      Assert.assertEquals("Filtered and unfiltered jobs must produce identical dependency links",
          baselineDeps, filteredDeps);
    } finally {
      cleanIndex(spanIndex);
      cleanIndex(depIndex);
      jaegerOpenSearchEnvironment.refresh();
      this.dependenciesJob = null;
    }
  }

  private String getDependenciesJson(String depIndex) throws IOException {
    Request request = new Request.Builder()
        .url(String.format("http://%s/%s/_search?size=100", jaegerOpenSearchEnvironment.getOpenSearchIPPort(), depIndex))
        .get()
        .build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      return response.body() != null ? response.body().string() : "";
    }
  }

  private void cleanIndex(String index) throws IOException {
    Request request = new Request.Builder()
        .url(String.format("http://%s/%s", jaegerOpenSearchEnvironment.getOpenSearchIPPort(), index))
        .delete()
        .build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      // ignore
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
