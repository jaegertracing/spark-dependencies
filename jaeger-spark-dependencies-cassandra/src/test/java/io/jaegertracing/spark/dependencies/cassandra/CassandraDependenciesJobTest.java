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
package io.jaegertracing.spark.dependencies.cassandra;

import static org.awaitility.Awaitility.await;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * @author Pavol Loffay
 */
public class CassandraDependenciesJobTest extends DependenciesTest {

  private static Network network;
  private static CassandraContainer cassandra;
  private static GenericContainer jaegerCollector;
  private static GenericContainer jaegerQuery;
  private static GenericContainer jaegerCassandraSchema;
  private static int cassandraPort;

  @BeforeClass
  public static void beforeClass() {
    network = Network.newNetwork();
    cassandra = new CassandraContainer<>("cassandra:4.1")
        .withNetwork(network)
        .withNetworkAliases("cassandra")
        .withExposedPorts(9042);
    cassandra.start();
    cassandraPort = cassandra.getMappedPort(9042);

    jaegerCassandraSchema = new GenericContainer<>("jaegertracing/jaeger-cassandra-schema:" + jaegerVersion())
        .withLogConsumer(frame -> System.out.print(frame.getUtf8String()))
        .withNetwork(network);
    jaegerCassandraSchema.start();
    /**
     * Wait until schema is created
     */
    await().until(() -> !jaegerCassandraSchema.isRunning());

    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:" + jaegerVersion())
        .withNetwork(network)
        .withEnv("CASSANDRA_SERVERS", "cassandra")
        .withEnv("CASSANDRA_KEYSPACE", "jaeger_v1_dc1")
        .withEnv("COLLECTOR_ZIPKIN_HOST_PORT", ":9411")
        .withEnv("COLLECTOR_QUEUE_SIZE", "100000")
        // older versions of jaeger were using 204 status code, now changed to 200
        .waitingFor(new BoundPortHttpWaitStrategy(14269).forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        // the first one is health check
        .withExposedPorts(14269, 14268, 9411);
    jaegerCollector.start();

    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:" + jaegerVersion())
        .withNetwork(network)
        .withEnv("CASSANDRA_SERVERS", "cassandra")
        .withEnv("CASSANDRA_KEYSPACE", "jaeger_v1_dc1")
        .waitingFor(new BoundPortHttpWaitStrategy(16687).forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(16687, 16686);
    jaegerQuery.start();

    queryUrl = String.format("http://localhost:%d", jaegerQuery.getMappedPort(16686));
    collectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(9411));
  }

  @AfterClass
  public static void afterClass() {
    Optional.of(cassandra).ifPresent(GenericContainer::close);
    Optional.of(jaegerCollector).ifPresent(GenericContainer::close);
    Optional.of(jaegerQuery).ifPresent(GenericContainer::close);
    Optional.of(jaegerCassandraSchema).ifPresent(GenericContainer::close);
  }

  @After
  public void after() {
    try (Cluster cluster = cassandra.getCluster(); Session session = cluster.newSession()) {
      session.execute("TRUNCATE jaeger_v1_dc1.traces");
      session.execute(String.format("TRUNCATE jaeger_v1_dc1.%s", dependenciesTable(session)));
    }
  }

  private String dependenciesTable(Session session) {
    try {
      session.execute("SELECT ts from jaeger_v1_dc1.dependencies_v2 limit 1;");
    } catch (Exception ex) {
      return "dependencies";
    }
    return "dependencies_v2";
  }

  @Override
  protected void deriveDependencies() {
    CassandraDependenciesJob.builder()
        .contactPoints("localhost:" + cassandraPort)
        .day(LocalDate.now())
        .keyspace("jaeger_v1_dc1")
        .username(cassandra.getUsername())
        .password(cassandra.getPassword())
        .build()
        .run("peer.service");
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // TODO otherwise it sometimes fails
    TimeUnit.SECONDS.sleep(1);
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
