package io.opentracing.spark.dependencies.elastic;


import com.github.dockerjava.api.model.Link;
import io.jaegertracing.spark.dependencies.common.JaegerTestDriverContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;

/**
 * @author Pavol Loffay
 */
public class ElasticSearchDependenciesJobTest {

  private GenericContainer elastic;
  private JaegerTestDriverContainer jaegerTestDriver;

  @Before
  public void before() {
    elastic = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.1")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withHostName("elasticsearch");
        })
        .withExposedPorts(9200, 9300)
        .waitingFor(Wait.forHttp("/")) // Wait until elastic start
        .withEnv("xpack.security.enabled", "false")
        .withEnv("network.host", "_site_")
        .withEnv("network.publish_host", "_local_");
    elastic.start();

    jaegerTestDriver = new JaegerTestDriverContainer("pavolloffay/jaeger-test-driver:viper")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withEnv(
              "COLLECTOR_ARGS=--span-storage.type=elasticsearch --es.server-urls=http://elasticsearch:9200",
              "QUERY_ARGS=--span-storage.type=elasticsearch --es.server-urls=http://elasticsearch:9200",
              "INITIALIZE_CASSANDRA=false");
          cmd.withLinks(new Link(elastic.getContainerId(), "elasticsearch"));
          cmd.withHostName("test_driver");
        })
        .withExposedPorts(14268, 16686, 8080, 9411);
    jaegerTestDriver.start();
  }

  @After
  public void after() {
    elastic.stop();
  }

  @Test
  public void test() {

  }
}
