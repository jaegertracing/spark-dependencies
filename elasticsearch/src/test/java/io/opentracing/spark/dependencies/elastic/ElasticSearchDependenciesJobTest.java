package io.opentracing.spark.dependencies.elastic;


import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Link;
import com.uber.jaeger.Tracer;
import io.jaegertracing.spark.dependencies.LogInitializer;
import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import io.jaegertracing.spark.dependencies.test.DependenciesDerivator;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.Traversals;
import io.jaegertracing.spark.dependencies.test.tree.TreeGenerator;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.Wait;

/**
 * @author Pavol Loffay
 */
public class ElasticSearchDependenciesJobTest {

  private OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

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
        .withExposedPorts(14268, 14269, 9411);
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

    Tracer initStorageTracer = TracersGenerator.createJaeger("init", collectorUrl);
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitRestTracesContains(initStorageTracer.getServiceName(), "foo");
  }

  @After
  public void after() {
    elasticsearch.stop();
    jaegerCollector.stop();
    jaegerQuery.stop();
  }

  @Test
  public void testJaeger() throws IOException {
    TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
        TracersGenerator.generateJaeger(2, collectorUrl));
    Node<JaegerWrapper> root = treeGenerator.generateTree(15, 3);
    Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
    treeGenerator.getTracers().forEach(tracer -> tracer.getTracer().close());
    waitRestTracesContains(root.getServiceName(), root.getTracingWrapper().operationName());

    ElasticsearchDependenciesJob.builder()
        .logInitializer(LogInitializer.create("INFO"))
        .hosts("http://localhost:"+elasticsearch.getMappedPort(9200))
        .day(System.currentTimeMillis())
        .build()
        .run();

    Request request = new Request.Builder()
        .url(queryUrl + "/api/dependencies?endTs=" + System.currentTimeMillis())
        .get()
        .build();
    DependenciesDerivator.serviceDependencies(root);
    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      RestResult<DependencyLink> restResult = objectMapper.readValue(response.body().string(), new TypeReference<RestResult<DependencyLink>>() {});
      assertEquals(null, restResult.getErrors());
      assertEquals(DependenciesDerivator.serviceDependencies(root), DependenciesDerivator.serviceDependencies(restResult.getData()));
    }
  }

  public void waitRestTracesContains(String service, String spanContainsThis) {
    Request request2 = new Request.Builder()
        .url(String.format("%s/api/traces?service=%s", queryUrl, service))
        .get()
        .build();
    await().atMost(1, TimeUnit.MINUTES).until(() -> {
      Response response = okHttpClient.newCall(request2).execute();
      String body = response.body().string();
      return body.contains(spanContainsThis);
    });
  }

}
