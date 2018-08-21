/**
 * Copyright 2017 The Jaeger Authors
 * Copyright 2016-2017 The OpenZipkin Authors
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.DependenciesSparkHelper;
import io.jaegertracing.spark.dependencies.Utils;
import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author OpenZipkin authors
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJob {
  private static final Logger log = LoggerFactory.getLogger(ElasticsearchDependenciesJob.class);

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    String hosts = Utils.getEnv("ES_NODES", "127.0.0.1");
    String username = Utils.getEnv("ES_USERNAME", null);
    String password = Utils.getEnv("ES_PASSWORD", null);
    Boolean clientNodeOnly = Boolean.parseBoolean(Utils.getEnv("ES_CLIENT_NODE_ONLY", "false"));
    String indexPrefix = Utils.getEnv("ES_INDEX_PREFIX", null);

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      // don't die if there are no spans
      sparkProperties.put("es.index.read.missing.as.empty", "true");
      sparkProperties.put("es.nodes.wan.only", Utils.getEnv("ES_NODES_WAN_ONLY", "false"));
      sparkProperties.put("es.net.ssl.keystore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.keyStore"));
      sparkProperties.put("es.net.ssl.keystore.pass",
          System.getProperty("javax.net.ssl.keyStorePassword", ""));
      sparkProperties.put("es.net.ssl.truststore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.trustStore"));
      sparkProperties.put("es.net.ssl.truststore.pass",
          System.getProperty("javax.net.ssl.trustStorePassword", ""));
    }

    // local[*] master lets us run & test the job locally without setting a Spark cluster
    String sparkMaster = Utils.getEnv("SPARK_MASTER", "local[*]");
    // needed when not in local mode
    String[] jars;

    // By default the job only works on traces whose first timestamp is today
    ZonedDateTime day = ZonedDateTime.of(LocalDate.now().atStartOfDay(), ZoneOffset.UTC);

    /** When set, this indicates which jars to distribute to the cluster. */
    public Builder jars(String... jars) {
      this.jars = jars;
      return this;
    }

    /** es.nodes separated by ',' */
    public Builder nodes(String hosts) {
      Utils.checkNoTNull(hosts, "nodes");
      this.hosts = hosts;
      sparkProperties.put("es.nodes.wan.only", "true");
      return this;
    }

    /** username used for basic auth. Needed when Shield or X-Pack security is enabled */
    public Builder username(String username) {
      this.username = username;
      return this;
    }

    /** password used for basic auth. Needed when Shield or X-Pack security is enabled */
    public Builder password(String password) {
      this.password = password;
      return this;
    }

    /** index prefix for Jaeger indices. By default empty */
    public Builder indexPrefix(String indexPrefix) {
      this.indexPrefix = indexPrefix;
      return this;
    }

    /** Day to process dependencies for. Defaults to today. */
    public Builder day(LocalDate day) {
      this.day = day.atStartOfDay(ZoneOffset.UTC);
      return this;
    }

    public ElasticsearchDependenciesJob build() {
      return new ElasticsearchDependenciesJob(this);
    }
  }

  private static String getSystemPropertyAsFileResource(String key) {
    String prop = System.getProperty(key, "");
    return prop != null && !prop.isEmpty() ? "file:" + prop : prop;
  }

  private final ZonedDateTime day;
  private final SparkConf conf;
  private final String indexPrefix;

  ElasticsearchDependenciesJob(Builder builder) {
    this.day = builder.day;
    this.conf = new SparkConf(true).setMaster(builder.sparkMaster).setAppName(getClass().getName());
    if (builder.jars != null) {
      conf.setJars(builder.jars);
    }
    if (builder.username != null) {
      conf.set("es.net.http.auth.user", builder.username);
    }
    if (builder.password != null) {
      conf.set("es.net.http.auth.pass", builder.password);
    }
    conf.set("es.nodes", builder.hosts);
    if (builder.hosts.indexOf("https") != -1) {
      conf.set("es.net.ssl", "true");
    }
    if (builder.clientNodeOnly) {
      conf.set("es.nodes.discovery", "0");
      conf.set("es.nodes.client.only", "1");
    }
    for (Map.Entry<String, String> entry : builder.sparkProperties.entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }

    String indexPrefix = builder.indexPrefix;
    if (indexPrefix != null) {
      indexPrefix = String.format("%s:", indexPrefix);
    } else {
      indexPrefix = "";
    }
    this.indexPrefix = indexPrefix;
  }

  public void run() {
    run(indexDate("jaeger-span"), indexDate("jaeger-dependencies") + "/dependencies");
  }

  String indexDate(String index) {
    String date = day.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    return String.format("%s%s-%s", indexPrefix, index, date);
  }

  void run(String spanResource, String depResource) {
    log.info("Running Dependencies job for {}, reading from {} index, result storing to {}", day, spanResource ,depResource);
    JavaSparkContext sc = new JavaSparkContext(conf);
    try {
      JavaPairRDD<String, Iterable<Span>> traces = JavaEsSpark.esJsonRDD(sc, spanResource)
          .map(new ElasticTupleToSpan())
          .groupBy(Span::getTraceId);

      List<Dependency> dependencyLinks = DependenciesSparkHelper.derive(traces);
      store(sc, dependencyLinks, depResource);
      log.info("Done, {} dependency objects created and stored to {}", dependencyLinks.size(), depResource);
    } finally {
      sc.stop();
    }
  }

  private void store(JavaSparkContext javaSparkContext, List<Dependency> dependencyLinks, String resource) {
    if (dependencyLinks.isEmpty()) {
      return;
    }

    String json;
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      json = objectMapper.writeValueAsString(new ElasticsearchDependencies(dependencyLinks, day));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize dependencies", e);
    }

    JavaEsSpark.saveJsonToEs(javaSparkContext.parallelize(Collections.singletonList(json)), resource);
  }

  /**
   * Helper class used to serialize dependencies to JSON.
   */
  public static final class ElasticsearchDependencies {
    private List<Dependency> dependencies;
    private ZonedDateTime ts;

    public ElasticsearchDependencies(List<Dependency> dependencies, ZonedDateTime ts) {
      this.dependencies = dependencies;
      this.ts = ts;
    }

    public List<Dependency> getDependencies() {
      return dependencies;
    }

    public String getTimestamp() {
      // Jaeger ES dependency storage uses RFC3339Nano for timestamp
      return ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
  }
}
