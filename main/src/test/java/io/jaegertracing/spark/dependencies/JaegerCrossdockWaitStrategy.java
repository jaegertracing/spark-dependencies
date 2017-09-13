package io.jaegertracing.spark.dependencies;

import static org.awaitility.Awaitility.await;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.WaitStrategy;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
public class JaegerCrossdockWaitStrategy implements WaitStrategy {
    protected Duration duration = Duration.ofMinutes(1);
    protected OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
    protected int originalStatusPort;

    public JaegerCrossdockWaitStrategy(int originalStatusPort) {
        this.originalStatusPort = originalStatusPort;
    }

    @Override
    public void waitUntilReady(GenericContainer container) {
        String url = String.format("http://localhost:%s/", container.getMappedPort(originalStatusPort));

        await().atMost(duration.toMillis(), TimeUnit.MILLISECONDS)
                .pollInterval(org.awaitility.Duration.TWO_SECONDS)
                .until(() -> {
                    Request request = new Request.Builder()
                            .url(url)
                            .head()
                            .build();

                    try {
                        Response response = okHttpClient.newCall(request).execute();
                        return response.code() == 200;
                    } catch (ConnectException ex) {
                        return false;
                    }
                });
    }

    @Override
    public WaitStrategy withStartupTimeout (Duration startupTimeout){
        return this;
    }
}

