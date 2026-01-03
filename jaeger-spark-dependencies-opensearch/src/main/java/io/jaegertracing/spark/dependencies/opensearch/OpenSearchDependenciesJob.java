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
package io.jaegertracing.spark.dependencies.opensearch;

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
import java.util.regex.Pattern;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.opensearch.spark.rdd.api.java.JavaOpenSearchSpark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author OpenZipkin authors
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
public class OpenSearchDependenciesJob {
  private static final Logger log = LoggerFactory.getLogger(OpenSearchDependenciesJob.class);
  private static final Pattern PORT_PATTERN = Pattern.compile(":\\d+");

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    String hosts = Utils.getEnv("OS_NODES", "127.0.0.1");
    String username = Utils.getEnv("OS_USERNAME", null);
    String password = Utils.getEnv("OS_PASSWORD", null);
    Boolean clientNodeOnly = Boolean.parseBoolean(Utils.getEnv("OS_CLIENT_NODE_ONLY", "false"));
    Boolean nodesWanOnly = Boolean.parseBoolean(Utils.getEnv("OS_NODES_WAN_ONLY", "false"));
    String indexPrefix = Utils.getEnv("OS_INDEX_PREFIX", null);
    String indexDatePattern = datePattern(Utils.getEnv("OS_INDEX_DATE_SEPARATOR", "-"));
    String spanRange = Utils.getEnv("OS_TIME_RANGE", "24h");
    Boolean useAliases = Boolean.parseBoolean(Utils.getEnv("OS_USE_ALIASES", "false"));
    Boolean allowSelfSigned = Boolean.parseBoolean(Utils.getEnv("OS_SSL_CERT_ALLOW_SELF_SIGNED", "false"));

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      // don't die if there are no spans
      sparkProperties.put("opensearch.index.read.missing.as.empty", "true");
      sparkProperties.put("opensearch.net.ssl.keystore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.keyStore"));
      sparkProperties.put("opensearch.net.ssl.keystore.pass",
          System.getProperty("javax.net.ssl.keyStorePassword", ""));
      sparkProperties.put("opensearch.net.ssl.truststore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.trustStore"));
      sparkProperties.put("opensearch.net.ssl.truststore.pass",
          System.getProperty("javax.net.ssl.trustStorePassword", ""));
      if (allowSelfSigned) {
        sparkProperties.put("opensearch.net.ssl.cert.allow.self.signed", "true");
      }

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

    /** opensearch.nodes separated by ',' */
    public Builder nodes(String hosts) {
      Utils.checkNoTNull(hosts, "nodes");
      this.hosts = hosts;
      this.nodesWanOnly = true;
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

    /** index date pattern for Jaeger indices. By default yyyy-MM-dd */
    public Builder indexDatePattern(String indexDatePattern) {
      this.indexDatePattern = indexDatePattern;
      return this;
    }

     /** span range for Jaeger indices. By default 24h */
    public Builder spanRange(String spanRange) {
      this.spanRange = spanRange;
      return this;
    }

    /** Day to process dependencies for. Defaults to today. */
    public Builder day(LocalDate day) {
      this.day = day.atStartOfDay(ZoneOffset.UTC);
      return this;
    }

    /** Whether the connector is used against an OpenSearch instance in a cloud/restricted
     *  environment over the WAN, such as Amazon Web Services. In this mode, the
     *  connector disables discovery and only connects through the declared opensearch.nodes during all operations,
     *  including reads and writes. Note that in this mode, performance is highly affected. */
    public Builder nodesWanOnly(boolean wanOnly) {
      this.nodesWanOnly = wanOnly;
      return this;
    }

    private static void logIfNoPort(String hosts) {
      if (!PORT_PATTERN.matcher(hosts).find()) {
        log.warn("Port is not specified, default port 9200 will be used");
      }
    }

    public OpenSearchDependenciesJob build() {
      String hosts = System.getenv("OS_NODES");
      String wanOnly = System.getenv("OS_NODES_WAN_ONLY");
      // Optimize user configuration - nodes specified but wan only not
      if (hosts != null && wanOnly == null) {
        this.nodesWanOnly = true;
      }
      logIfNoPort(this.hosts);
      return new OpenSearchDependenciesJob(this);
    }
  }

  private static String getSystemPropertyAsFileResource(String key) {
    String prop = System.getProperty(key, "");
    return prop != null && !prop.isEmpty() ? "file:" + prop : prop;
  }

  private final ZonedDateTime day;
  private final SparkConf conf;
  private final String indexPrefix;
  private final String indexDatePattern;
  private final String spanRange;
  private final Boolean useAliases;

  OpenSearchDependenciesJob(Builder builder) {
    this.day = builder.day;
    this.conf = new SparkConf(true).setMaster(builder.sparkMaster).setAppName(getClass().getName());
    if (builder.jars != null) {
      conf.setJars(builder.jars);
    }
    if (builder.username != null) {
      conf.set("opensearch.net.http.auth.user", builder.username);
    }
    if (builder.password != null) {
      conf.set("opensearch.net.http.auth.pass", builder.password);
    }
    conf.set("opensearch.nodes", builder.hosts);
    if (builder.hosts.indexOf("https") != -1) {
      conf.set("opensearch.net.ssl", "true");
    }
    if (builder.nodesWanOnly) {
      conf.set("opensearch.nodes.wan.only", "true");
    }
    if (builder.clientNodeOnly) {
      conf.set("opensearch.nodes.discovery", "0");
      conf.set("opensearch.nodes.client.only", "1");
    }
    for (Map.Entry<String, String> entry : builder.sparkProperties.entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }
    this.indexPrefix = builder.indexPrefix;
    this.indexDatePattern = builder.indexDatePattern;
    this.spanRange = builder.spanRange;
    this.useAliases = builder.useAliases;
  }

  /**
   * https://github.com/jaegertracing/jaeger/blob/master/CHANGELOG.md#190-2019-01-21
   */
  private static String prefixBefore19(String prefix) {
    return prefix != null ? String.format("%s:", prefix) : "";
  }

  private static String prefix(String prefix) {
    return prefix != null ? String.format("%s-", prefix) : "";
  }

  private static String datePattern(String separator) {
    if (separator.equals("")) {
      return "yyyyMMdd";
    }
    // ' is escape character in date format, we should double it here.
    if (separator.contains("'")) {
      separator = separator.replace("'", "''");
    }
    return String.format("yyyy'%s'MM'%s'dd", separator, separator);
  }

  public void run(String peerServiceTag) {

    String[] readIndices;
    String[] writeIndex;

    // use alias indices common when using index rollover
    if (this.useAliases) {
      readIndices = new String[]{prefix(indexPrefix) + "jaeger-span-read", prefixBefore19(indexPrefix) + "jaeger-span-read"};
      writeIndex = new String[] {prefix(indexPrefix) + "jaeger-dependencies-write", prefixBefore19(indexPrefix) + "jaeger-dependencies-write"};
    }
    else {
      readIndices = indexDate("jaeger-span");
      writeIndex = indexDate("jaeger-dependencies");
    }

    run(readIndices, writeIndex, peerServiceTag);
  }

  String[] indexDate(String index) {
    String date = day.toLocalDate().format(DateTimeFormatter.ofPattern(indexDatePattern));
    if (indexPrefix != null && indexPrefix.length() > 0) {
      return new String[]{String.format("%s%s-%s", prefix(indexPrefix), index, date), String.format("%s%s-%s", prefixBefore19(indexPrefix), index, date)};
    }
    // if there is no prefix we read and write only to one index
    return new String[]{String.format("%s-%s", index, date)};
  }

  void run(String[] spanIndices, String[] depIndices,String peerServiceTag) {
    JavaSparkContext sc = new JavaSparkContext(conf);
    try {
      for (int i = 0; i < spanIndices.length; i++) {
        String spanIndex = spanIndices[i];
        String depIndex = depIndices[i];
        log.info("Running Dependencies job for {}, reading from {} index, result storing to {}", day, spanIndex, depIndex);
        // Send raw query to OS to select only the docs / spans we want to consider for this job
        // This doesn't change the default behavior as the daily indexes only contain up to 24h of data
        String osQuery = String.format("{\"range\": {\"startTimeMillis\": { \"gte\": \"now-%s\" }}}", spanRange);
        JavaPairRDD<String, Iterable<Span>> traces = JavaOpenSearchSpark.opensearchRDD(sc, spanIndex, osQuery)
            .map(new OpenSearchTupleToSpan())
            .groupBy(Span::getTraceId);
        List<Dependency> dependencyLinks = DependenciesSparkHelper.derive(traces,peerServiceTag);
        
        // No version check needed for OpenSearch as we don't support types in indexes
        store(sc, dependencyLinks, depIndex);
        log.info("Done, {} dependency objects created", dependencyLinks.size());
        if (dependencyLinks.size() > 0) {
          // we do not derive dependencies for old prefix "prefix:" if new prefix "prefix-" contains data
          break;
        }
      }
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
      json = objectMapper.writeValueAsString(new OpenSearchDependencies(dependencyLinks, day));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize dependencies", e);
    }

    JavaOpenSearchSpark.saveJsonToOpenSearch(javaSparkContext.parallelize(Collections.singletonList(json)), resource);
  }

  /**
   * Helper class used to serialize dependencies to JSON.
   */
  public static final class OpenSearchDependencies {
    private List<Dependency> dependencies;
    private ZonedDateTime ts;

    public OpenSearchDependencies(List<Dependency> dependencies, ZonedDateTime ts) {
      this.dependencies = dependencies;
      this.ts = ts;
    }

    public List<Dependency> getDependencies() {
      return dependencies;
    }

    public String getTimestamp() {
      // Jaeger OS dependency storage uses RFC3339Nano for timestamp
      return ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
  }

  /**
   * Entry point for running OpenSearchDependenciesJob directly.
   */
  public static void main(String[] args) throws java.io.UnsupportedEncodingException {
    LocalDate date = LocalDate.now();
    if (args.length == 1) {
      date = LocalDate.parse(args[0]);
    } else if (System.getenv("DATE") != null) {
      date = LocalDate.parse(System.getenv("DATE"));
    }

    String peerServiceTag = System.getenv("PEER_SERVICE_TAG");
    if (peerServiceTag == null) {
      peerServiceTag = "peer.service";
    }

    String jarPath = Utils.pathToUberJar(OpenSearchDependenciesJob.class);
    OpenSearchDependenciesJob.builder()
        .jars(jarPath)
        .day(date)
        .build()
        .run(peerServiceTag);
  }
}
