/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.spark.dependencies.cassandra;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.awaitility.Awaitility;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;

/**
 * @author Pavol Loffay
 */
public class JaegerTestDriverContainer extends GenericContainer<JaegerTestDriverContainer> {
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
    Awaitility.await().atMost(waitUntilReady.toMillis(), TimeUnit.MILLISECONDS)
        .pollInterval(org.awaitility.Duration.TWO_SECONDS)
        .until(containerStartedCondition(statusUrl));
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
      try {
        Response response = okHttpClient.newCall(request).execute();
        return response.code() == 200;
      } catch (ConnectException ex) {
        return false;
      }
    };
  }
}
