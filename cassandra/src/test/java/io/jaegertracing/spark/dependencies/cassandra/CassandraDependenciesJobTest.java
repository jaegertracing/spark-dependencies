package io.jaegertracing.spark.dependencies.cassandra;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import brave.Tracing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Link;
import com.uber.jaeger.Tracer;
import io.jaegertracing.spark.dependencies.LogInitializer;
import io.jaegertracing.spark.dependencies.test.DependenciesDerivator;
import io.jaegertracing.spark.dependencies.test.JaegerTestDriverContainer;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.test.rest.RestResult;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import io.jaegertracing.spark.dependencies.test.tree.Traversals;
import io.jaegertracing.spark.dependencies.test.tree.TreeGenerator;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * @author Pavol Loffay
 */
public class CassandraDependenciesJobTest {

    private OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
    private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

    protected static int cassandraPort;
    protected static String queryUrl;
    protected static String collectorUrl;
    protected static String zipkinCollectorUrl;

    protected GenericContainer cassandra;
    protected GenericContainer jaegerTestDriver;

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


    @Test
    public void testJaeger() throws IOException {
        TreeGenerator<Tracer> treeGenerator = new TreeGenerator(
            TracersGenerator.generateJaeger(2, collectorUrl));
        Node<JaegerWrapper> root = treeGenerator.generateTree(15, 3);
        Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
        treeGenerator.getTracers().forEach(tracer -> tracer.getTracer().close());
        waitRestTracesContains(root.getServiceName(), root.getTracingWrapper().operationName());

        CassandraDependenciesJob.builder()
                .logInitializer(LogInitializer.create("INFO"))
                .contactPoints("localhost:" + cassandraPort)
                .day(System.currentTimeMillis())
                .keyspace("jaeger")
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

    @Test
    public void testZipkin() throws IOException {
        TreeGenerator<Tracing> treeGenerator = new TreeGenerator(TracersGenerator.generateZipkin(2, zipkinCollectorUrl));
        Node<ZipkinWrapper> root = treeGenerator.generateTree(15, 3);
        Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
        treeGenerator.getTracers().forEach(tracer -> tracer.getTracer().close());

        waitRestTracesContains(root.getServiceName(), root.getTracingWrapper().operationName());

        CassandraDependenciesJob.builder()
            .logInitializer(LogInitializer.create("INFO"))
            .contactPoints("localhost:" + cassandraPort)
            .day(System.currentTimeMillis())
            .keyspace("jaeger")
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
