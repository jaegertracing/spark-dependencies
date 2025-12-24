/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.cassandra;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.traits.LinkableContainer;

/**
 * @author Pavol Loffay
 */
public class JaegerTestDriverContainer extends GenericContainer<JaegerTestDriverContainer>
    implements LinkableContainer {
  protected final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  protected final Duration waitUntilReady;

  public JaegerTestDriverContainer(String dockerImageName) {
    this(dockerImageName, Duration.ofMinutes(1));
  }

  public JaegerTestDriverContainer(String dockerImageName, Duration waitUntilReady) {
    super(dockerImageName);
    this.waitUntilReady = waitUntilReady;
  }

  @Override
  protected void waitUntilContainerStarted() {
    String statusUrl = String.format("http://localhost:%d/", this.getMappedPort(8080));
    Unreliables.retryUntilTrue((int)waitUntilReady.toMillis(), TimeUnit.MILLISECONDS, containerStartedCondition(statusUrl));
  }

  protected Callable<Boolean> containerStartedCondition(String statusUrl) {
    return () -> {
      if (!isRunning()) {
        throw new ContainerLaunchException("Container failed to start");
      }

      Request request = new Request.Builder()
          .url(statusUrl)
          .head()
          .build();
      try (Response response = okHttpClient.newCall(request).execute()) {
        return response.code() == 200;
      } catch (ConnectException ex) {
        return false;
      }
    };
  }
}
