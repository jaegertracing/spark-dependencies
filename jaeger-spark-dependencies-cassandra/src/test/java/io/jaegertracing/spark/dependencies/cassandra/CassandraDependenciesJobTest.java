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

import com.datastax.oss.driver.api.core.CqlSession;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * @author Pavol Loffay
 */
public class CassandraDependenciesJobTest extends DependenciesTest {

  protected static Network network;
  protected static CassandraContainer cassandra;
  protected static GenericContainer jaegerAll;
  protected static GenericContainer jaegerCassandraSchema;
  private static int cassandraPort;

  @BeforeClass
  public static void beforeClass() {
    System.out.println("=== Starting CassandraDependenciesJobTest setup ===");
    
    network = Network.newNetwork();
    System.out.println("Created network: " + network.getId());
    
    System.out.println("Starting Cassandra container (cassandra:4.1)...");
    cassandra = new CassandraContainer("cassandra:4.1")
        .withNetwork(network)
        .withNetworkAliases("cassandra")
        .withExposedPorts(9042);
    cassandra.start();
    cassandraPort = cassandra.getMappedPort(9042);
    System.out.println("Cassandra started. Mapped port: " + cassandraPort);

    System.out.println("Starting Jaeger Cassandra schema container (jaegertracing/jaeger-cassandra-schema:" + jaegerVersion() + ")...");
    jaegerCassandraSchema = new GenericContainer<>("jaegertracing/jaeger-cassandra-schema:" + jaegerVersion())
        .withLogConsumer(new LogToConsolePrinter("[jaeger-cassandra-schema] "))
        .withNetwork(network);
    jaegerCassandraSchema.start();
    System.out.println("Jaeger Cassandra schema container started, waiting for schema creation...");
    /**
     * Wait until schema is created
     */
    await().until(() -> !jaegerCassandraSchema.isRunning());
    System.out.println("Jaeger Cassandra schema creation completed");

    System.out.println("Starting Jaeger v2 unified container (jaegertracing/jaeger:" + jaegerVersion() + ")...");
    jaegerAll = new GenericContainer<>("jaegertracing/jaeger:" + jaegerVersion())
        .withNetwork(network)
        .withClasspathResourceMapping("jaeger-v2-config-cassandra.yaml", "/etc/jaeger/config.yaml", org.testcontainers.containers.BindMode.READ_ONLY)
        .withCommand("--config", "/etc/jaeger/config.yaml")
        .waitingFor(new BoundPortHttpWaitStrategy(16687).forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(16687, 16686, 4317, 4318, 14268, 9411);
    jaegerAll.start();
    System.out.println("Jaeger v2 container started");

    queryUrl = String.format("http://localhost:%d", jaegerAll.getMappedPort(16686));
    collectorUrl = String.format("http://localhost:%d", jaegerAll.getMappedPort(4317));
    
    System.out.println("=== Container setup complete ===");
    System.out.println("Query URL: " + queryUrl);
    System.out.println("Collector URL: " + collectorUrl);
    System.out.println("Health check port: " + jaegerAll.getMappedPort(16687));
  }

  @AfterClass
  public static void afterClass() {
    Optional.of(cassandra).ifPresent(GenericContainer::close);
    Optional.of(jaegerAll).ifPresent(GenericContainer::close);
    Optional.of(jaegerCassandraSchema).ifPresent(GenericContainer::close);
  }

  @After
  public void after() {
    try (CqlSession session = CqlSession.builder()
            .addContactPoint(cassandra.getContactPoint())
            .withLocalDatacenter(cassandra.getLocalDatacenter())
            .build()) {
      session.execute("TRUNCATE jaeger_v1_dc1.traces");
      session.execute(String.format("TRUNCATE jaeger_v1_dc1.%s", dependenciesTable(session)));
    }
  }

  private String dependenciesTable(CqlSession session) {
    try {
      session.execute("SELECT ts from jaeger_v1_dc1.dependencies_v2 limit 1;");
    } catch (Exception ex) {
      return "dependencies";
    }
    return "dependencies_v2";
  }

  @Override
  protected void deriveDependencies() {
    System.out.println("::group::🚧 🚧 🚧 CassandraDependenciesJob logs");
    try {
      CassandraDependenciesJob.builder()
          .contactPoints("localhost:" + cassandraPort)
          .day(LocalDate.now())
          .keyspace("jaeger_v1_dc1")
          .username(cassandra.getUsername())
          .password(cassandra.getPassword())
          .build()
          .run("peer.service");
    } finally {
      System.out.println("::endgroup::");
    }
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
