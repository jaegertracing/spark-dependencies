/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import static io.jaegertracing.spark.dependencies.test.DependenciesTest.jaegerVersion;

import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJobTest.BoundPortHttpWaitStrategy;
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
 */
public class JaegerElasticsearchEnvironment {

  private OkHttpClient okHttpClient = new OkHttpClient();
  Network network;
  GenericContainer elasticsearch;
  GenericContainer jaegerAll;

  /**
   * Set these in subclasses
   */
  private String queryUrl;
  private String collectorUrl;

  public static String elasticsearchVersion() {
    String version = System.getProperty("elasticsearch.version", System.getenv("ELASTICSEARCH_VERSION"));
    return version != null ? version : "7.17.10";
  }

  public void start(Map<String, String> jaegerEnvs, String jaegerVersion, String elasticsearchVersion) {
    network = Network.newNetwork();
    elasticsearch = new GenericContainer<>(String.format("docker.elastic.co/elasticsearch/elasticsearch:%s", elasticsearchVersion))
        .withNetwork(network)
        .withNetworkAliases("elasticsearch")
        .waitingFor(new BoundPortHttpWaitStrategy(9200).forStatusCode(200))
        .withExposedPorts(9200, 9300)
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("network.bind_host", "elasticsearch")
        .withEnv("network.host", "_site_")
        .withEnv("network.publish_host", "_local_");
    elasticsearch.start();

    jaegerAll = new GenericContainer<>("jaegertracing/jaeger:" + jaegerVersion)
        .withNetwork(network)
        .withClasspathResourceMapping("jaeger-v2-config-elasticsearch.yaml", "/etc/jaeger/config.yaml", org.testcontainers.containers.BindMode.READ_ONLY)
        .withCommand("--config", "/etc/jaeger/config.yaml")
        .withEnv(jaegerEnvs)
        .waitingFor(new BoundPortHttpWaitStrategy(16687).forStatusCodeMatching(statusCode -> statusCode >= 200 && statusCode < 300))
        .withExposedPorts(16687, 16686, 4317, 4318, 14268, 9411);
    jaegerAll.start();

    collectorUrl = String.format("http://%s:%d", jaegerAll.getContainerIpAddress(), jaegerAll.getMappedPort(4317));
    queryUrl = String.format("http://%s:%d", jaegerAll.getContainerIpAddress(), jaegerAll.getMappedPort(16686));
  }

  public void cleanUp(String[] spanIndex, String[] dependenciesIndex) throws IOException {
      String matchAllQuery = "{\"query\": {\"match_all\":{} }}";
      Request request = new Request.Builder()
          .url(String.format("http://%s:%d/%s,%s/_delete_by_query?conflicts=proceed",
              elasticsearch.getContainerIpAddress(),
              elasticsearch.getMappedPort(9200),
              // we don't use index prefix
              spanIndex[0],
              dependenciesIndex[0]))
          .post(
              RequestBody.create(MediaType.parse("application/json; charset=utf-8"), matchAllQuery))
          .build();


      try (Response response =  okHttpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String body = response.body().string();
          throw new IllegalStateException(String.format("Could not remove data from ES: %s, %s", response, body));
        }
      }
  }

  /**
   * In Elasticsearch, the _refresh endpoint is used to make recently indexed,
   * updated, or deleted documents visible to search, as otherwise they might
   * be still sitting in a memory buffer.
   */
  public void refresh() throws IOException {
    Request request = new Request.Builder()
        .url(String.format("http://%s:%d/_refresh",
            elasticsearch.getContainerIpAddress(),
            elasticsearch.getMappedPort(9200)))
        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ""))
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String body = response.body().string();
        throw new IllegalStateException(String.format("Could not refresh ES: %s, %s", response, body));
      }
    }
  }

  public void stop() {
    Optional.of(jaegerAll).ifPresent(GenericContainer::close);
    Optional.of(elasticsearch).ifPresent(GenericContainer::close);
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

  public String getElasticsearchIPPort() {
    return String.format("%s:%d", elasticsearch.getContainerIpAddress(), elasticsearch.getMappedPort(9200));
  }
}
