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

import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for OpenSearch backend support
 */
public class OpenSearchDependenciesJobTest extends DependenciesTest {
  
  private static Network network;
  private static GenericContainer<?> opensearch;
  private static GenericContainer<?> jaegerCollector;
  private static GenericContainer<?> jaegerQuery;
  private ElasticsearchDependenciesJob dependenciesJob;
  
  @BeforeClass
  public static void beforeClass() {
    network = Network.newNetwork();
    
    // Start OpenSearch container
    opensearch = new GenericContainer<>("opensearchproject/opensearch:2.11.0")
        .withStartupTimeout(Duration.ofMinutes(5))
        .withNetwork(network)
        .withNetworkAliases("opensearch")
        .withEnv("discovery.type", "single-node")
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
        .waitingFor(new HttpWaitStrategy()
            .forPort(9200)
            .forStatusCode(200))
        .withExposedPorts(9200);
    opensearch.start();
    
    // Start Jaeger collector with OpenSearch storage
    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:" + jaegerVersion())
        .withStartupTimeout(Duration.ofMinutes(3))
        .withNetwork(network)
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://opensearch:9200")
        .withEnv("COLLECTOR_ZIPKIN_HOST_PORT", "9411")
        .withEnv("COLLECTOR_QUEUE_SIZE", "100000")
        .waitingFor(new HttpWaitStrategy()
            .forPort(14269)
            .forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(14269, 14268, 9411);
    jaegerCollector.start();
    
    // Start Jaeger query with OpenSearch storage
    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:" + jaegerVersion())
        .withStartupTimeout(Duration.ofMinutes(3))
        .withNetwork(network)
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://opensearch:9200")
        .waitingFor(new HttpWaitStrategy()
            .forPort(16687)
            .forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(16687, 16686);
    jaegerQuery.start();
    
    // Setup URLs
    collectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://localhost:%d", jaegerQuery.getMappedPort(16686));
  }
  
  @AfterClass
  public static void afterClass() {
    Optional.ofNullable(jaegerQuery).ifPresent(GenericContainer::close);
    Optional.ofNullable(jaegerCollector).ifPresent(GenericContainer::close);
    Optional.ofNullable(opensearch).ifPresent(GenericContainer::close);
    Optional.ofNullable(network).ifPresent(n -> {
      try {
        n.close();
      } catch (Exception e) {
        // ignore
      }
    });
  }
  
  @After
  public void after() throws IOException {
    // Clean up data for next test
    if (dependenciesJob != null) {
      // Delete indices (OpenSearch specific cleanup if needed)
    }
  }
  
  @Override
  protected void deriveDependencies() {
    dependenciesJob = ElasticsearchDependenciesJob.builder()
        .nodes(String.format("http://localhost:%d", opensearch.getMappedPort(9200)))
        .day(LocalDate.now())
        .build();
    
    dependenciesJob.run("peer.service");
  }
  
  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // OpenSearch indexing delay
    TimeUnit.SECONDS.sleep(2);
  }
}
