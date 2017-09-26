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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.github.dockerjava.api.model.Link;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.GenericContainer;

/**
 * @author Pavol Loffay
 */
public class CassandraDependenciesJobTest extends DependenciesTest {

  private static CassandraContainer cassandra;
  private static GenericContainer jaegerTestDriver;
  private static int cassandraPort;

  @BeforeClass
  public static void beforeClass() throws TimeoutException {
    cassandra = new CassandraContainer("cassandra:3.9")
        .withExposedPorts(9042);
    cassandra.start();
    cassandraPort = cassandra.getMappedPort(9042);

    jaegerTestDriver = new JaegerTestDriverContainer("jaegertracing/test-driver:latest")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withLinks(new Link(cassandra.getContainerId(), "cassandra"));
          cmd.withHostName("test_driver");
        })
        .withExposedPorts(14268, 16686, 8080, 9411);
    jaegerTestDriver.start();
    queryUrl = String.format("http://localhost:%d", jaegerTestDriver.getMappedPort(16686));
    collectorUrl = String.format("http://localhost:%d", jaegerTestDriver.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://localhost:%d", jaegerTestDriver.getMappedPort(9411));
  }

  @AfterClass
  public static void afterClass() {
    Optional.of(cassandra).ifPresent(GenericContainer::close);
    Optional.of(jaegerTestDriver).ifPresent(GenericContainer::close);
  }

  @After
  public void after() {
    try (Cluster cluster = cassandra.getCluster(); Session session = cluster.newSession()) {
      session.execute("TRUNCATE jaeger.traces");
      session.execute("TRUNCATE jaeger.dependencies");

    }
  }

  @Override
  protected void deriveDependencies() throws Exception {
    // flush all data to disk
    cassandra.execInContainer("nodetool", "flush", "jaeger");

    CassandraDependenciesJob.builder()
        .contactPoints("localhost:" + cassandraPort)
        .day(LocalDate.now())
        .keyspace("jaeger")
        .build()
        .run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // TODO otherwise it sometimes fails
    TimeUnit.SECONDS.sleep(1);
  }
}
