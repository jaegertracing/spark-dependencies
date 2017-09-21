package io.opentracing.spark.dependencies.elastic;


import com.github.dockerjava.api.model.Link;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;

/**
 * @author Pavol Loffay
 */
public class ElasticSearchDependenciesJobTest {

  private GenericContainer elasticsearch;
  private GenericContainer jaegerCollector;
  private GenericContainer jaegerQuery;

  private String collectorUrl;
  private String zipkinCollectorUrl;
  private String queryUrl;

  @Before
  public void before() {
    elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.1")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withHostName("elasticsearch");
        })
        .withExposedPorts(9200, 9300)
        .waitingFor(Wait.forHttp("/")) // Wait until elastic start
        .withEnv("xpack.security.enabled", "false")
        .withEnv("network.host", "_site_")
        .withEnv("network.publish_host", "_local_");
    elasticsearch.start();

    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:latest")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withCmd("/go/bin/collector-linux",
              "--es.server-urls=http://elasticsearch:9200",
              "--span-storage.type=elasticsearch",
              "--collector.zipkin.http-port=9411");
          cmd.withLinks(new Link(elasticsearch.getContainerId(), "elasticsearch"));
        })
        .withExposedPorts(14268, 9411);
    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:latest")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withCmd("/go/bin/query-linux",
              "--es.server-urls=http://elasticsearch:9200",
              "--span-storage.type=elasticsearch");
          cmd.withLinks(new Link(elasticsearch.getContainerId(), "elasticsearch"));
        })
        .withExposedPorts(16686);

    jaegerQuery.start();
    jaegerCollector.start();

    collectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://localhost:%d", jaegerQuery.getMappedPort(16686));
  }

  @After
  public void after() {
    elasticsearch.stop();
    jaegerCollector.stop();
    jaegerQuery.stop();
  }

  @Test
  public void test() {

  }
}
