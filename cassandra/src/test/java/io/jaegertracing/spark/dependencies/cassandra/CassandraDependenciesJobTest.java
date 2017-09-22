package io.jaegertracing.spark.dependencies.cassandra;

import com.github.dockerjava.api.model.Link;
import io.jaegertracing.spark.dependencies.LogInitializer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.testcontainers.containers.GenericContainer;

/**
 * @author Pavol Loffay
 */
public class CassandraDependenciesJobTest extends DependenciesTest {

  private GenericContainer cassandra;
  private GenericContainer jaegerTestDriver;
  private int cassandraPort;

  @Before
  public void before() throws TimeoutException {
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

  @After
  public void after() {
    cassandra.stop();
    jaegerTestDriver.stop();
  }

  @Override
  protected void deriveDependencies() throws Exception {
    // flush all date to the storage
    cassandra.execInContainer("nodetool", "flush", "jaeger");

    CassandraDependenciesJob.builder()
        .logInitializer(LogInitializer.create("INFO"))
        .contactPoints("localhost:" + cassandraPort)
        .day(System.currentTimeMillis())
        .keyspace("jaeger")
        .build()
        .run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
//    TimeUnit.SECONDS.sleep(2);
  }
}
