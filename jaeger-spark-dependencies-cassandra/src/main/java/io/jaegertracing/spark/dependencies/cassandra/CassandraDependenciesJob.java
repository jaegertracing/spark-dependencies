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
package io.jaegertracing.spark.dependencies.cassandra;

import static com.datastax.spark.connector.japi.CassandraJavaUtil.javaFunctions;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.mapRowTo;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.mapToRow;

import com.google.common.base.Joiner;
import com.google.common.net.HostAndPort;
import io.jaegertracing.spark.dependencies.DependenciesSparkHelper;
import io.jaegertracing.spark.dependencies.Utils;
import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

/**
 * @author OpenZipkin authors
 * @author Pavol Loffay
 */
public final class CassandraDependenciesJob {
  private static final Logger log = LoggerFactory.getLogger(CassandraDependenciesJob.class);

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    String keyspace = Utils.getEnv("CASSANDRA_KEYSPACE", "jaeger_v1_dc1");
    String contactPoints = Utils.getEnv("CASSANDRA_CONTACT_POINTS", "localhost");
    String localDc = Utils.getEnv("CASSANDRA_LOCAL_DC", null);
    // local[*] master lets us run & test the job locally without setting a Spark cluster
    String sparkMaster = Utils.getEnv("SPARK_MASTER", "local[*]");
    // needed when not in local mode
    String[] jars;

    // By default the job only works on traces whose first timestamp is today
    ZonedDateTime day = ZonedDateTime.of(LocalDate.now().atStartOfDay(), ZoneOffset.UTC);

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      sparkProperties.put("spark.cassandra.connection.ssl.enabled",
          Utils.getEnv("CASSANDRA_USE_SSL", "false"));
      sparkProperties.put("spark.cassandra.connection.ssl.trustStore.password",
          System.getProperty("javax.net.ssl.trustStorePassword", ""));
      sparkProperties.put("spark.cassandra.connection.ssl.trustStore.path",
          System.getProperty("javax.net.ssl.trustStore", ""));
      sparkProperties.put("spark.cassandra.connection.ssl.clientAuth.enabled",
          Utils.getEnv("CASSANDRA_SSL_KEYSTORE", "").isEmpty() ? "false" : "true");
      sparkProperties.put("spark.cassandra.connection.ssl.keyStore.path",
          Utils.getEnv("CASSANDRA_SSL_KEYSTORE", ""));
      sparkProperties.put("spark.cassandra.connection.ssl.keyStore.password",
          Utils.getEnv("CASSANDRA_SSL_KEYSTORE_PASSWORD", ""));
      sparkProperties.put("spark.cassandra.auth.username", Utils.getEnv("CASSANDRA_USERNAME", ""));
      sparkProperties.put("spark.cassandra.auth.password", Utils.getEnv("CASSANDRA_PASSWORD", ""));
    }

    /** When set, this indicates which jars to distribute to the cluster. */
    public Builder jars(String... jars) {
      this.jars = jars;
      return this;
    }

    /** Keyspace to store dependency rowsToLinks. Defaults to "jaeger_v1_test" */
    public Builder keyspace(String keyspace) {
      Utils.checkNoTNull("keyspace", keyspace);
      this.keyspace = keyspace;
      return this;
    }

    /** Day to process dependencies for. Defaults to today. */
    public Builder day(LocalDate day) {
      this.day = day.atStartOfDay(ZoneOffset.UTC);
      return this;
    }

    /** Comma separated list of hosts / IPs part of Cassandra cluster. Defaults to localhost */
    public Builder contactPoints(String contactPoints) {
      this.contactPoints = contactPoints;
      return this;
    }

    /** The local DC to connect to (other nodes will be ignored) */
    public Builder localDc(String localDc) {
      this.localDc = localDc;
      return this;
    }

    public CassandraDependenciesJob build() {
      return new CassandraDependenciesJob(this);
    }
  }

  private final String keyspace;
  private final ZonedDateTime day;
  private final SparkConf conf;

  CassandraDependenciesJob(Builder builder) {
    this.keyspace = builder.keyspace;
    this.day = builder.day;
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.conf = new SparkConf(true)
        .setMaster(builder.sparkMaster)
        .setAppName(getClass().getName());
    conf.set("spark.cassandra.connection.host", parseHosts(builder.contactPoints));
    conf.set("spark.cassandra.connection.port", parsePort(builder.contactPoints));
    if (builder.localDc != null) {
      conf.set("connection.local_dc", builder.localDc);
    }
    if (builder.jars != null) {
      conf.setJars(builder.jars);
    }
    for (Map.Entry<String, String> entry : builder.sparkProperties.entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }
  }

  public void run() {
    long microsLower = day.toInstant().toEpochMilli() * 1000;
    long microsUpper = day.plus(Period.ofDays(1)).toInstant().toEpochMilli() * 1000 - 1;

    log.info("Running Dependencies job for {}: {} ≤ Span.timestamp {}", day, microsLower, microsUpper);
    JavaSparkContext sc = new JavaSparkContext(conf);
    try {
      JavaPairRDD<String, Iterable<Span>> traces = javaFunctions(sc)
          .cassandraTable(keyspace, "traces", mapRowTo(Span.class))
          .where("start_time < ? AND start_time > ?", microsUpper, microsLower)
          .mapToPair(span -> new Tuple2<>(span.getTraceId(), span))
          .groupByKey();

      List<Dependency> dependencyLinks = DependenciesSparkHelper.derive(traces);
      store(sc, dependencyLinks);
      log.info("Done, {} dependency objects created", dependencyLinks.size());
    } finally {
      sc.stop();
    }
  }

  private void store(JavaSparkContext sc, List<Dependency> links) {
    CassandraDependencies dependencies = new CassandraDependencies(links, day);
    javaFunctions(sc.parallelize(Collections.singletonList(dependencies)))
        .writerBuilder(keyspace, "dependencies", mapToRow(CassandraDependencies.class))
        .saveToCassandra();
  }

  static String parseHosts(String contactPoints) {
    List<String> result = new LinkedList<>();
    for (String contactPoint : contactPoints.split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      result.add(parsed.getHostText());
    }
    return Joiner.on(',').join(result);
  }

  /** Returns the consistent port across all contact points or 9042 */
  static String parsePort(String contactPoints) {
    Set<Integer> ports = new HashSet<>();
    for (String contactPoint: contactPoints.split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      ports.add(parsed.getPortOrDefault(9042));
    }
    return ports.size() == 1 ? String.valueOf(ports.iterator().next()) : "9042";
  }

  /**
   * DTO object used to store dependencies to Cassandra, see {@link com.datastax.spark.connector.mapper.JavaBeanColumnMapper}
   */
  public final static class CassandraDependencies implements Serializable {
    private static final long serialVersionUID = 0L;

    private List<Dependency> dependencies;
    private ZonedDateTime zonedDateTime;

    public CassandraDependencies(List<Dependency> dependencies, ZonedDateTime ts) {
      this.dependencies = dependencies;
      this.zonedDateTime = ts;
    }

    public List<Dependency> getDependencies() {
      return dependencies;
    }

    public Long getTs() {
      return zonedDateTime.toInstant().toEpochMilli();
    }

    public Long getTsIndex() {
      return zonedDateTime.toInstant().toEpochMilli();
    }
  }
}

