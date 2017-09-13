package io.jaegertracing.spark.dependencies;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.WaitingConsumer;

import com.github.dockerjava.api.model.Link;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;

import io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob;
import io.opentracing.Span;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
public class CassandraJobTest {

    private static OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();

    private static int queryPort;
    private static int collectorPort;
    private static int cassandraPort;

    @BeforeClass
    public static void beforeClass() throws TimeoutException {
        GenericContainer cassandra =
                new GenericContainer<>("cassandra:3.9")
                        .withCreateContainerCmdModifier(cmd -> cmd.withHostName("cassandra"))
                        .withExposedPorts(9042);
        cassandra.start();
        waitForCassandra(cassandra);
        cassandraPort = cassandra.getMappedPort(9042);

        GenericContainer jaegerTestDriver =
                new GenericContainer<>("jaegertracing/test-driver:latest")
                        .withCreateContainerCmdModifier(cmd -> {
                            cmd.withLinks(new Link(cassandra.getContainerId(), "cassandra"));
                            cmd.withHostName("test_driver");
                        })
                    .withExposedPorts(14268, 16686, 8080);
        jaegerTestDriver.start();
        new JaegerCrossdockWaitStrategy(8080).waitUntilReady(jaegerTestDriver);
        queryPort = jaegerTestDriver.getMappedPort(16686);
        collectorPort = jaegerTestDriver.getMappedPort(14268);
    }

    protected static void waitForCassandra(GenericContainer cassandra) throws TimeoutException {
        WaitingConsumer consumer = new WaitingConsumer();
        cassandra.followOutput(consumer, STDOUT);
        consumer.waitUntil(frame ->
                frame.getUtf8String().contains("Starting listening for CQL clients"), 1, TimeUnit.MINUTES);
    }

    @Test
    public void testA() throws IOException {
        Tracer t1 = createTracer("service1");
        Span span1 = t1.buildSpan("foo").startManual();

        Tracer t2 = createTracer("service2");
        t2.buildSpan("foo").asChildOf(span1).startManual().finish();
        t2.close();

        span1.finish();
        t1.close();

        Request request2 = new Request.Builder()
                .url("http://localhost:"+queryPort+"/api/traces?service=service1")
                .get()
                .build();
        await().atMost(2, TimeUnit.MINUTES).until(() -> {
            Response response = okHttpClient.newCall(request2).execute();
            String body = response.body().string();
            return body.contains("foo");
        });

        CassandraDependenciesJob.builder()
                .logInitializer(LogInitializer.create("INFO"))
                .contactPoints("localhost:" + cassandraPort)
                .day(System.currentTimeMillis())
                .keyspace("jaeger")
                .build()
                .run();

        Request request = new Request.Builder()
                .url("http://localhost:"+queryPort+"/api/dependencies?endTs=" + System.currentTimeMillis())
                .get()
                .build();

        await().until(() -> {
            Response response = okHttpClient.newCall(request).execute();
            String body = response.body().string();
            return body.contains("service1") && body.contains("service2");
        });

        try (Response response = okHttpClient.newCall(request).execute()) {
            assertEquals(200, response.code());
            String body = response.body().string();
            assertTrue(body.contains("service1"));
            assertTrue(body.contains("service2"));
            assertTrue(body.contains("\"callCount\":1"));
        }
    }

    protected com.uber.jaeger.Tracer createTracer(String serviceName) {
        return new com.uber.jaeger.Tracer.Builder(serviceName,
                new RemoteReporter(new HttpSender("http://localhost:"+collectorPort+"/api/traces", 65000), 1, 100,
                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))), new ConstSampler(true))
                .build();
    }
}
