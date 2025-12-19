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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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
import org.elasticsearch.hadoop.rest.RestClient;
import org.elasticsearch.hadoop.util.EsMajorVersion;
import org.elasticsearch.spark.cfg.SparkSettings;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;
import org.opensearch.spark.rdd.api.java.JavaOpenSearchSpark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author OpenZipkin authors
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJob {
  private static final Logger log = LoggerFactory.getLogger(ElasticsearchDependenciesJob.class);
  private static final Pattern PORT_PATTERN = Pattern.compile(":\\d+");

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    String hosts = Utils.getEnv("ES_NODES", "127.0.0.1");
    String username = Utils.getEnv("ES_USERNAME", null);
    String password = Utils.getEnv("ES_PASSWORD", null);
    Boolean clientNodeOnly = Boolean.parseBoolean(Utils.getEnv("ES_CLIENT_NODE_ONLY", "false"));
    Boolean nodesWanOnly = Boolean.parseBoolean(Utils.getEnv("ES_NODES_WAN_ONLY", "false"));
    String indexPrefix = Utils.getEnv("ES_INDEX_PREFIX", null);
    String indexDatePattern = datePattern(Utils.getEnv("ES_INDEX_DATE_SEPARATOR", "-"));
    String spanRange = Utils.getEnv("ES_TIME_RANGE", "24h");
    Boolean useAliases = Boolean.parseBoolean(Utils.getEnv("ES_USE_ALIASES", "false"));

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      // don't die if there are no spans
      sparkProperties.put("es.index.read.missing.as.empty", "true");
      sparkProperties.put("es.net.ssl.keystore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.keyStore"));
      sparkProperties.put("es.net.ssl.keystore.pass",
          System.getProperty("javax.net.ssl.keyStorePassword", ""));
      sparkProperties.put("es.net.ssl.truststore.location",
          getSystemPropertyAsFileResource("javax.net.ssl.trustStore"));
      sparkProperties.put("es.net.ssl.truststore.pass",
          System.getProperty("javax.net.ssl.trustStorePassword", ""));
      sparkProperties.put("es.net.ssl.cert.allow.self.signed",
          Utils.getEnv("ES_NET_SSL_CERT_ALLOW_SELF_SIGNED", "false"));
      sparkProperties.put("opensearch.net.ssl.cert.allow.self.signed",
          Utils.getEnv("ES_NET_SSL_CERT_ALLOW_SELF_SIGNED", "false"));
      if (Boolean.parseBoolean(Utils.getEnv("ES_NET_SSL", "false"))) {
        sparkProperties.put("es.net.ssl", "true");
        sparkProperties.put("opensearch.net.ssl", "true");
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

    /** es.nodes separated by ',' */
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

    /** Whether the connector is used against an Elasticsearch instance in a cloud/restricted
     *  environment over the WAN, such as Amazon Web Services. In this mode, the
     *  connector disables discovery and only connects through the declared es.nodes during all operations,
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

    public ElasticsearchDependenciesJob build() {
      String hosts = System.getenv("ES_NODES");
      String wanOnly = System.getenv("ES_NODES_WAN_ONLY");
      // Optimize user configuration - nodes specified but wan only not
      if (hosts != null && wanOnly == null) {
        this.nodesWanOnly = true;
      }
      logIfNoPort(this.hosts);
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
  private final String indexDatePattern;
  private final String spanRange;
  private final Boolean useAliases;

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
    if (builder.nodesWanOnly) {
      conf.set("es.nodes.wan.only", "true");
    }
    if (builder.clientNodeOnly) {
      conf.set("es.nodes.discovery", "0");
      conf.set("es.nodes.client.only", "1");
    }
    // Mirror configuration for OpenSearch connector (dual-backend support)
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
      boolean isOpenSearch = isOpenSearchCluster();
      for (int i = 0; i < spanIndices.length; i++) {
        String spanIndex = spanIndices[i];
        String depIndex = depIndices[i];
        log.info("Running Dependencies job for {}, reading from {} index, result storing to {}", day, spanIndex, depIndex);
        // Send raw query to ES to select only the docs / spans we want to consider for this job
        // This doesn't change the default behavior as the daily indexes only contain up to 24h of data
        String esQuery = String.format("{\"range\": {\"startTimeMillis\": { \"gte\": \"now-%s\" }}}", spanRange);
        JavaPairRDD<String, Iterable<Span>> traces;
        if (isOpenSearch) {
          // Use OpenSearch connector for reads
    traces = JavaOpenSearchSpark.openSearchJsonRDD(sc, spanIndex, esQuery)
              .map(new ElasticTupleToSpan())
              .groupBy(Span::getTraceId);
        } else {
          traces = JavaEsSpark.esJsonRDD(sc, spanIndex, esQuery)
              .map(new ElasticTupleToSpan())
              .groupBy(Span::getTraceId);
        }
        List<Dependency> dependencyLinks = DependenciesSparkHelper.derive(traces,peerServiceTag);
        if (!isOpenSearch) {
          EsMajorVersion esMajorVersion = getEsVersion();
          // Add type for ES < 7
          // WARN log is produced for older ES versions, however it's produced by spark-es library and not ES itself, it cannot be disabled
          //  WARN Resource: Detected type name in resource [jaeger-dependencies-2019-08-14/dependencies]. Type names are deprecated and will be removed in a later release.
          if (esMajorVersion.before(EsMajorVersion.V_7_X)) {
            depIndex = depIndex + "/dependencies";
          }
        }
        if (isOpenSearch) {
          // Always no types on OpenSearch
          storeToOpenSearch(sc, dependencyLinks, depIndex);
        } else {
          store(sc, dependencyLinks, depIndex);
        }
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

  // Visible for tests
  boolean isOpenSearchCluster() {
    String nodes = conf.get("es.nodes", "");
    if (nodes == null || nodes.isEmpty()) {
      log.warn("No nodes configured, cannot detect cluster type");
      return false;
    }
    String[] hosts = nodes.split(",");
    String host = hosts[0];
    if (!host.startsWith("http")) {
      // Assuming http for detection if not specified (SSL usually handled by
      // es.net.ssl)
      boolean ssl = conf.getBoolean("es.net.ssl", false) || conf.getBoolean("opensearch.net.ssl", false);
      host = (ssl ? "https://" : "http://") + host;
    }

    HttpURLConnection connection = null;
    log.info("Starting OpenSearch detection for host: {}", host);
    try {
      URL url = URI.create(host).toURL();
      connection = (HttpURLConnection) url.openConnection();

      if (connection instanceof HttpsURLConnection) {
        boolean allowSelfSigned = conf.getBoolean("es.net.ssl.cert.allow.self.signed", false)
            || conf.getBoolean("opensearch.net.ssl.cert.allow.self.signed", false);
        log.info("Connection is HTTPS. Allow Self Signed: {}", allowSelfSigned);
        if (allowSelfSigned) {
          try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                  public X509Certificate[] getAcceptedIssuers() {
                    return null;
                  }

                  public void checkClientTrusted(X509Certificate[] certs, String authType) {
                  }

                  public void checkServerTrusted(X509Certificate[] certs, String authType) {
                  }
                }
            };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
            ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
            log.info("TrustAll SSL context configured successfully");
          } catch (Exception e) {
            log.warn("Failed to configure SSL for self-signed certs: {}", e.getMessage());
          }
        }
      }

      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      String username = conf.contains("es.net.http.auth.user") ? conf.get("es.net.http.auth.user") : null;
      if (username == null && conf.contains("opensearch.net.http.auth.user")) {
        username = conf.get("opensearch.net.http.auth.user");
      }
      String password = conf.contains("es.net.http.auth.pass") ? conf.get("es.net.http.auth.pass") : null;
      if (password == null && conf.contains("opensearch.net.http.auth.pass")) {
        password = conf.get("opensearch.net.http.auth.pass");
      }

      log.info("Auth configured: username={}", username);

      if (username != null && password != null) {
        String auth = username + ":" + password;
        String encodedAuth = java.util.Base64.getEncoder()
            .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
      }

      int responseCode = connection.getResponseCode();
      log.info("Response Code: {}", responseCode);
      if (responseCode >= 200 && responseCode < 300) {
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          StringBuilder response = new StringBuilder();
          String inputLine;
          while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
          }
          log.info("Successfully received cluster info response for backend detection: {}", response);
          return isOpenSearchFromJson(response.toString());
        }
      } else {
        log.warn("Failed to get cluster info, response code: {}", responseCode);
        try (BufferedReader err = new BufferedReader(
            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
          StringBuilder errParams = new StringBuilder();
          String errLine;
          while ((errLine = err.readLine()) != null)
            errParams.append(errLine);
          log.warn("Error stream: {}", errParams);
        } catch (Exception ignore) {
        }
      }
    } catch (Exception e) {
      log.warn("Could not detect cluster type, assuming Elasticsearch. Error: {}", e.getMessage(), e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
    return false;
  }


   boolean isOpenSearchFromJson(String clusterInfo) throws IOException {
    if (clusterInfo == null || clusterInfo.isEmpty()) return false;
    ObjectMapper mapper = new ObjectMapper();
    Map<?, ?> root = mapper.readValue(clusterInfo, Map.class);
    Object verObj = root.get("version");
    if (verObj instanceof Map) {
      Map<?, ?> version = (Map<?, ?>) verObj;
      Object dist = version.get("distribution");
      return dist != null && "opensearch".equalsIgnoreCase(String.valueOf(dist));
    }
    return false;
  }


  /**
   * Detects Elasticsearch major version to determine document type behavior.
   * Only called when backend is confirmed to be Elasticsearch (not OpenSearch).
   * 
   * @return EsMajorVersion indicating whether document types are needed
   */
  private EsMajorVersion getEsVersion() {
    RestClient client = new RestClient(new SparkSettings(conf));
    try {
      return client.mainInfo().getMajorVersion();
    } catch (Exception e) {
      log.error("Could not detect Elasticsearch version. Ensure the cluster is accessible and GET / endpoint is not blocked.", e);
      throw new IllegalStateException("Failed to detect Elasticsearch version", e);
    } finally {
      client.close();
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

  private void storeToOpenSearch(JavaSparkContext javaSparkContext, List<Dependency> dependencyLinks, String resource) {
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

JavaOpenSearchSpark.saveJsonToOpenSearch(
        javaSparkContext.parallelize(Collections.singletonList(json)), resource);
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

  /**
   * Entry point for running ElasticsearchDependenciesJob directly.
   * This is used when the Docker image variant is elasticsearch-specific.
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

    String jarPath = Utils.pathToUberJar(ElasticsearchDependenciesJob.class);
    ElasticsearchDependenciesJob.builder()
        .jars(jarPath)
        .day(date)
        .build()
        .run(peerServiceTag);
  }
}
