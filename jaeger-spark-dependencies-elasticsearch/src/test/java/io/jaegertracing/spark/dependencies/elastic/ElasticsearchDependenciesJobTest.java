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


import io.jaegertracing.Tracer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJobTest extends DependenciesTest {

  private static Network network;
  private static GenericContainer elasticsearch;
  private static GenericContainer jaegerCollector;
  private static GenericContainer jaegerQuery;

  private ElasticsearchDependenciesJob dependenciesJob;

  @BeforeClass
  public static void beforeClass() {
    network = Network.newNetwork();
    elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.9")
        .withNetwork(network)
        .withNetworkAliases("elasticsearch")
        .waitingFor(new BoundPortHttpWaitStrategy(9200).forStatusCode(200))
        .withExposedPorts(9200, 9300)
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("network.bind_host", "elasticsearch")
        .withEnv("network.host", "_site_")
        .withEnv("network.publish_host", "_local_");
    elasticsearch.start();

    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:" + jaegerVersion())
        .withNetwork(network)
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://elasticsearch:9200")
        .withEnv("COLLECTOR_ZIPKIN_HTTP_PORT", "9411")
        .withEnv("COLLECTOR_QUEUE_SIZE", "100000")
        .waitingFor(new BoundPortHttpWaitStrategy(14269).forStatusCode(204))
        // the first one is health check
        .withExposedPorts(14269, 14268, 9411);
    jaegerCollector.start();

    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:" + jaegerVersion())
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://elasticsearch:9200")
        .withNetwork(network)
        .waitingFor(new BoundPortHttpWaitStrategy(16687).forStatusCode(204))
        .withExposedPorts(16687, 16686);
    jaegerQuery.start();

    collectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://%s:%d", jaegerQuery.getContainerIpAddress(), jaegerQuery.getMappedPort(16686));
  }

  @Before
  public void before() {
    Tracer initStorageTracer = TracersGenerator.createJaeger(UUID.randomUUID().toString(), collectorUrl).getA();
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitJaegerQueryContains(initStorageTracer.getServiceName(), "foo");
  }

  @After
  public void after() throws IOException {
    if (dependenciesJob != null) {
      String matchAllQuery = "{\"query\": {\"match_all\":{} }}";
      Request request = new Request.Builder()
          .url(String.format("http://%s:%d/%s,%s/_delete_by_query?conflicts=proceed",
              elasticsearch.getContainerIpAddress(),
              elasticsearch.getMappedPort(9200),
              dependenciesJob.indexDate("jaeger-span"),
              dependenciesJob.indexDate("jaeger-dependencies")))
          .post(
              RequestBody.create(MediaType.parse("application/json; charset=utf-8"), matchAllQuery))
          .build();

      Response response = okHttpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        throw new IllegalStateException("Could not remove data from ES");
      }
    }
  }

  @AfterClass
  public static void afterClass() {
    Optional.of(jaegerCollector).ifPresent(GenericContainer::close);
    Optional.of(jaegerQuery).ifPresent(GenericContainer::close);
    Optional.of(elasticsearch).ifPresent(GenericContainer::close);
    Optional.of(network).ifPresent(network1 -> {
      try {
        network1.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  @Override
  protected void deriveDependencies() {
    dependenciesJob = ElasticsearchDependenciesJob.builder()
        .nodes("http://" + elasticsearch.getContainerIpAddress() + ":" + elasticsearch
            .getMappedPort(9200))
        .day(LocalDate.now())
        .build();
    dependenciesJob.run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // TODO otherwise elastic drops some spans
    TimeUnit.SECONDS.sleep(2);
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
