package io.opentracing.spark.dependencies.elastic;


import com.github.dockerjava.api.model.Link;
import com.uber.jaeger.Tracer;
import io.jaegertracing.spark.dependencies.LogInitializer;
import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;

/**
 * @author Pavol Loffay
 */
public class ElasticSearchDependenciesJobTest extends DependenciesTest {

  private GenericContainer elasticsearch;
  private GenericContainer jaegerCollector;
  private GenericContainer jaegerQuery;

  @Before
  public void before() {
    elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.1")
        .withCreateContainerCmdModifier(cmd -> cmd.withHostName("elasticsearch"))
        .withExposedPorts(9200, 9300)
        .waitingFor(Wait.forHttp("/"))
        .withEnv("xpack.security.enabled", "false")
        .withEnv("network.host", "_site_")
        .withEnv("network.publish_host", "_local_");
    elasticsearch.start();

    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:latest")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withCmd("/go/bin/collector-linux",
              "--es.server-urls=http://elasticsearch:9200",
              "--span-storage.type=elasticsearch",
              "--collector.zipkin.http-port=9411",
              "--collector.queue-size=100000",
              "--collector.num-workers=500");
          cmd.withLinks(new Link(elasticsearch.getContainerId(), "elasticsearch"));
        })
        .waitingFor(Wait.forHttp("/").forStatusCode(204))
        // the first one is health check
        .withExposedPorts(14269, 14268, 14269, 9411);
    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:latest")
        .withCreateContainerCmdModifier(cmd -> {
          cmd.withCmd("/go/bin/query-linux",
              "--es.server-urls=http://elasticsearch:9200",
              "--span-storage.type=elasticsearch");
          cmd.withLinks(new Link(elasticsearch.getContainerId(), "elasticsearch"));
        })
        .waitingFor(Wait.forHttp("/").forStatusCode(204))
        .withExposedPorts(16687, 16686);

    jaegerQuery.start();
    jaegerCollector.start();

    collectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://localhost:%d", jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://localhost:%d", jaegerQuery.getMappedPort(16686));

    Tracer initStorageTracer = TracersGenerator.createJaeger("init-elasticsearch", collectorUrl).getA();
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitJaegerQueryContains(initStorageTracer.getServiceName(), "foo");
  }

  @After
  public void after() {
    elasticsearch.stop();
    jaegerCollector.stop();
    jaegerQuery.stop();
  }

  @Override
  protected void deriveDependencies() {
    ElasticsearchDependenciesJob.builder()
        .logInitializer(LogInitializer.create("INFO"))
        .hosts("http://localhost:" + elasticsearch.getMappedPort(9200))
        .day(System.currentTimeMillis())
        .build()
        .run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    //otherwise elastic drops some spans
    TimeUnit.SECONDS.sleep(1);
  }
}
