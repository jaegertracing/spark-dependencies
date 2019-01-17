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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/**
 * @author Pavol Loffay
 */
public class JaegerElasticsearchEnvironment {

  private OkHttpClient okHttpClient = new OkHttpClient();
  private Network network;
  private GenericContainer elasticsearch;
  private GenericContainer jaegerCollector;
  private GenericContainer jaegerQuery;

  private static final Logger log = LoggerFactory.getLogger(JaegerElasticsearchEnvironment.class);
  private static Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

  /**
   * Set these in subclasses
   */
  private String queryUrl;
  private String collectorUrl;
  private String zipkinCollectorUrl;

  public void start(Map<String, String> jaegerEnvs, String jaegerVersion) {
    network = Network.newNetwork();
    elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.9")
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
    elasticsearch.followOutput(logConsumer);

    jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:" + jaegerVersion)
        .withNetwork(network)
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://elasticsearch:9200")
        .withEnv("COLLECTOR_ZIPKIN_HTTP_PORT", "9411")
        .withEnv("COLLECTOR_QUEUE_SIZE", "100000")
        .withEnv("LOG_LEVEL","DEBUG")
        .withEnv(jaegerEnvs)
        .waitingFor(new BoundPortHttpWaitStrategy(14269).forStatusCode(204))
        // the first one is health check
        .withExposedPorts(14269, 14268, 9411);
    jaegerCollector.start();
    jaegerCollector.followOutput(logConsumer);

    jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:" + jaegerVersion())
        .withEnv("SPAN_STORAGE_TYPE", "elasticsearch")
        .withEnv("ES_SERVER_URLS", "http://elasticsearch:9200")
        .withEnv("ES_TAGS_AS_FIELDS_ALL", "true")
        .withEnv("LOG_LEVEL","DEBUG")
        .withNetwork(network)
        .withEnv(jaegerEnvs)
        .waitingFor(new BoundPortHttpWaitStrategy(16687).forStatusCode(204))
        .withExposedPorts(16687, 16686);
    jaegerQuery.start();
    jaegerQuery.followOutput(logConsumer);

    collectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://%s:%d", jaegerQuery.getContainerIpAddress(), jaegerQuery.getMappedPort(16686));
  }

  public void cleanUp(String spanIndex, String dependenciesIndex) throws IOException {
      String matchAllQuery = "{\"query\": {\"match_all\":{} }}";
      Request request = new Request.Builder()
          .url(String.format("http://%s:%d/%s,%s/_delete_by_query?conflicts=proceed",
              elasticsearch.getContainerIpAddress(),
              elasticsearch.getMappedPort(9200),
              spanIndex,
              dependenciesIndex))
          .post(
              RequestBody.create(MediaType.parse("application/json; charset=utf-8"), matchAllQuery))
          .build();


      try (Response response =  okHttpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          throw new IllegalStateException("Could not remove data from ES");
        }
      }
  }

  public void stop() {
    Optional.of(jaegerCollector).ifPresent(GenericContainer::close);
    Optional.of(jaegerQuery).ifPresent(GenericContainer::close);
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

  public String getZipkinCollectorUrl() {
    return zipkinCollectorUrl;
  }

  public String getElasticsearchIPPort() {
    return String.format("%s:%d", elasticsearch.getContainerIpAddress(), elasticsearch.getMappedPort(9200));
  }
}