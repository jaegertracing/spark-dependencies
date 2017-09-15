package io.jaegertracing.spark.dependencies;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Link;
import io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob;
import io.jaegertracing.spark.dependencies.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.rest.JsonHelper;
import io.jaegertracing.spark.dependencies.rest.RestResult;
import io.jaegertracing.spark.dependencies.tree.Node;
import io.jaegertracing.spark.dependencies.tree.Traversals;
import io.jaegertracing.spark.dependencies.tree.TreeGenerator;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * @author Pavol Loffay
 */
public class CassandraJobTest {

    private static OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();

    private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

    protected static int cassandraPort;
    protected static String queryUrl;
    protected static String collectorUrl;

    @BeforeClass
    public static void beforeClass() throws TimeoutException {
        GenericContainer cassandra = new CassandraContainer("cassandra:3.9")
                        .withExposedPorts(9042);
        cassandra.start();
        cassandraPort = cassandra.getMappedPort(9042);

        GenericContainer jaegerTestDriver = new JaegerTestDriverContainer("jaegertracing/test-driver:latest")
                        .withCreateContainerCmdModifier(cmd -> {
                            cmd.withLinks(new Link(cassandra.getContainerId(), "cassandra"));
                            cmd.withHostName("test_driver");
                        })
                    .withExposedPorts(14268, 16686, 8080);
        jaegerTestDriver.start();
        queryUrl = String.format("http://localhost:%d", jaegerTestDriver.getMappedPort(16686));
        collectorUrl = String.format("http://localhost:%d", jaegerTestDriver.getMappedPort(14268));
    }

    @Test
    public void testA() throws IOException {
        TreeGenerator treeGenerator = new TreeGenerator(TracersGenerator.generate(2, collectorUrl));
        Node root = treeGenerator.generateTree(15, 3);
        Traversals.inorder(root, (node, parent) -> node.getSpan().finish());
        treeGenerator.getTracers().forEach(tracer -> tracer.close());

        Request request2 = new Request.Builder()
                .url(queryUrl+"/api/traces?service=" + root.getServiceName())
                .get()
                .build();
        await().atMost(2, TimeUnit.MINUTES).until(() -> {
            Response response = okHttpClient.newCall(request2).execute();
            String body = response.body().string();
            return body.contains(root.getSpan().getOperationName());
        });

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
        await().until(() -> {
            Response response = okHttpClient.newCall(request).execute();
            RestResult restResult = objectMapper.readValue(response.body().string(), RestResult.class);
            return restResult.getData() != null && restResult.getData().size() >= 1;
        });

        DependenciesDerivator.serviceDependencies(root);
        try (Response response = okHttpClient.newCall(request).execute()) {
            assertEquals(200, response.code());
            RestResult<DependencyLink> restResult = objectMapper.readValue(response.body().string(), new TypeReference<RestResult<DependencyLink>>() {});
            assertEquals(null, restResult.getErrors());
            assertEquals(DependenciesDerivator.serviceDependencies(root), DependenciesDerivator.serviceDependencies(restResult.getData()));
        }
    }
}
