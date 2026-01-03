/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class JaegerOpenSearchEnvironment {

  private OkHttpClient okHttpClient = new OkHttpClient();
  Network network;
  GenericContainer<?> opensearch;
  GenericContainer<?> jaegerAll;

  /**
   * Set these in subclasses
   */
  private String queryUrl;
  private String collectorUrl;

  public static String opensearchVersion() {
    String version = System.getProperty("opensearch.version", System.getenv("OPENSEARCH_VERSION"));
    return version != null ? version : "2.11.1";
  }

  public void start(Map<String, String> jaegerEnvs, String jaegerVersion, String opensearchVersion) {
    network = Network.newNetwork();
    opensearch = new GenericContainer<>(String.format("opensearchproject/opensearch:%s", opensearchVersion))
        .withNetwork(network)
        .withNetworkAliases("opensearch")
        .waitingFor(new BoundPortHttpWaitStrategy(9200).forStatusCode(200))
        .withExposedPorts(9200)
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("discovery.type", "single-node")
        .withEnv("network.bind_host", "opensearch")
        .withEnv("network.host", "0.0.0.0");
    opensearch.start();

    jaegerAll = new GenericContainer<>("jaegertracing/jaeger:" + jaegerVersion)
        .withNetwork(network)
        .withClasspathResourceMapping("jaeger-v2-config-opensearch.yaml", "/etc/jaeger/config.yaml",
            org.testcontainers.containers.BindMode.READ_ONLY)
        .withCommand("--config", "/etc/jaeger/config.yaml")
        .withEnv(jaegerEnvs)
        .waitingFor(new BoundPortHttpWaitStrategy(16687)
            .forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(16687, 16686, 4317, 4318, 14268, 9411);
    jaegerAll.start();

    collectorUrl = String.format("http://%s:%d", jaegerAll.getHost(), jaegerAll.getMappedPort(4317));
    queryUrl = String.format("http://%s:%d", jaegerAll.getHost(), jaegerAll.getMappedPort(16686));
  }

  public void cleanUp(String[] spanIndex, String[] dependenciesIndex) throws IOException {
    String matchAllQuery = "{\"query\": {\"match_all\":{} }}";
    Request request = new Request.Builder()
        .url(String.format("http://%s:%d/%s,%s/_delete_by_query?conflicts=proceed",
            opensearch.getHost(),
            opensearch.getMappedPort(9200),
            // we don't use index prefix
            spanIndex[0],
            dependenciesIndex[0]))
        .post(
            RequestBody.create(MediaType.parse("application/json; charset=utf-8"), matchAllQuery))
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String body = response.body().string();
        throw new IllegalStateException(String.format("Could not remove data from OS: %s, %s", response, body));
      }
    }
  }

  /**
   * In OpenSearch, the _refresh endpoint is used to make recently indexed,
   * updated, or deleted documents visible to search, as otherwise they might
   * be still sitting in a memory buffer.
   */
  public void refresh() throws IOException {
    Request request = new Request.Builder()
        .url(String.format("http://%s:%d/_refresh",
            opensearch.getHost(),
            opensearch.getMappedPort(9200)))
        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ""))
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String body = response.body().string();
        throw new IllegalStateException(String.format("Could not refresh OS: %s, %s", response, body));
      }
    }
  }

  public void stop() {
    Optional.of(jaegerAll).ifPresent(GenericContainer::close);
    Optional.of(opensearch).ifPresent(GenericContainer::close);
    Optional.of(network).ifPresent(network1 -> {
      try {
        network1.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  public String getQueryUrl() {
    return queryUrl;
  }

  public String getCollectorUrl() {
    return collectorUrl;
  }

  public String getOpenSearchIPPort() {
    return String.format("%s:%d", opensearch.getHost(), opensearch.getMappedPort(9200));
  }

  public static class BoundPortHttpWaitStrategy extends org.testcontainers.containers.wait.strategy.HttpWaitStrategy {
    private final int port;

    public BoundPortHttpWaitStrategy(int port) {
      this.port = port;
    }

    @Override
    protected java.util.Set<Integer> getLivenessCheckPorts() {
      int mapptedPort = this.waitStrategyTarget.getMappedPort(port);
      return java.util.Collections.singleton(mapptedPort);
    }
  }
}
