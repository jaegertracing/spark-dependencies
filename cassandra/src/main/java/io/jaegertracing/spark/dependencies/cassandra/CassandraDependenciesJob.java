package io.jaegertracing.spark.dependencies.cassandra;

import static com.datastax.spark.connector.japi.CassandraJavaUtil.javaFunctions;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.mapRowTo;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.mapToRow;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import io.jaegertracing.spark.dependencies.DependencyLinks;
import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

public final class CassandraDependenciesJob {
  private static final Logger log = LoggerFactory.getLogger(CassandraDependenciesJob.class);

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    String keyspace = getEnv("CASSANDRA_KEYSPACE", "jaeger_v1_test");
    String contactPoints = getEnv("CASSANDRA_CONTACT_POINTS", "localhost");
    String localDc = getEnv("CASSANDRA_LOCAL_DC", null);
    // local[*] master lets us run & test the job locally without setting a Spark cluster
    String sparkMaster = getEnv("SPARK_MASTER", "local[*]");
    // needed when not in local mode
    String[] jars;
    Runnable logInitializer;

    // By default the job only works on traces whose first timestamp is today
    long day = midnightUTC(System.currentTimeMillis());

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      sparkProperties.put("spark.cassandra.connection.ssl.enabled",
          getEnv("CASSANDRA_USE_SSL", "false"));
      sparkProperties.put("spark.cassandra.connection.ssl.trustStore.password",
          System.getProperty("javax.net.ssl.trustStorePassword", ""));
      sparkProperties.put("spark.cassandra.connection.ssl.trustStore.path",
          System.getProperty("javax.net.ssl.trustStore", ""));
      sparkProperties.put("spark.cassandra.auth.username", getEnv("CASSANDRA_USERNAME", ""));
      sparkProperties.put("spark.cassandra.auth.password", getEnv("CASSANDRA_PASSWORD", ""));
    }

    /** When set, this indicates which jars to distribute to the cluster. */
    public Builder jars(String... jars) {
      this.jars = jars;
      return this;
    }

    /** Keyspace to store dependency rowsToLinks. Defaults to "jaeger_v1_test" */
    public Builder keyspace(String keyspace) {
      checkNoNull("keyspace", keyspace);
      this.keyspace = keyspace;
      return this;
    }

    /** Day (in epoch milliseconds) to process dependencies for. Defaults to today. */
    public Builder day(long day) {
      this.day = midnightUTC(day);
      return this;
    }

    /** Ensures that logging is setup. Particularly important when in cluster mode. */
    public Builder logInitializer(Runnable logInitializer) {
      checkNoNull("logInitializer", logInitializer);
      this.logInitializer = logInitializer;
      return this;
    }

    /** Comma separated list of hosts / IPs part of Cassandra cluster. Defaults to localhost */
    public Builder contactPoints( String contactPoints) {
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

  private static String getEnv(String key, String defaultValue) {
    String result = System.getenv(key);
    return result != null ? result : defaultValue;
  }

  final String keyspace;
  final long day;
  final String dateStamp;
  final SparkConf conf;
  final Runnable logInitializer;

  CassandraDependenciesJob(Builder builder) {
    this.keyspace = builder.keyspace;
    this.day = builder.day;
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.dateStamp = df.format(new Date(builder.day));
    this.conf = new SparkConf(true)
        .setMaster(builder.sparkMaster)
        .setAppName(getClass().getName());
    conf.set("spark.cassandra.connection.host", parseHosts(builder.contactPoints));
    conf.set("spark.cassandra.connection.port", parsePort(builder.contactPoints));
    if (builder.localDc != null) conf.set("connection.local_dc", builder.localDc);
    if (builder.jars != null) conf.setJars(builder.jars);
    for (Map.Entry<String, String> entry : builder.sparkProperties.entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }
    this.logInitializer = builder.logInitializer;
  }

  public void run() {
    long microsLower = day * 1000;
    long microsUpper = (day * 1000) + TimeUnit.DAYS.toMicros(1) - 1;

    log.info("Running Dependencies job for {}: {} ≤ Span.timestamp {}", dateStamp, microsLower,
        microsUpper);

    JavaSparkContext sc = new JavaSparkContext(conf);
    JavaPairRDD<String, Iterable<Span>> traces = javaFunctions(sc)
        .cassandraTable(keyspace, "traces", mapRowTo(Span.class))
        .where("start_time < ? AND start_time > ?", microsUpper, microsLower)
        .spanBy(Span::getTraceId, String.class);

    save(sc, deps(traces));
    sc.stop();
  }

  public static List<Dependency> deps(JavaPairRDD<String, Iterable<Span>> traces) {
        return traces.flatMapValues(DependencyLinks.dependencyLinks()).values()
        .mapToPair(dependency -> tuple2(tuple2(dependency.getParent(), dependency.getChild()), dependency))
        .reduceByKey((link1, link2) ->
            new Dependency(link1.getParent(), link2.getChild(), link1.getCallCount() + link2.getCallCount()))
        .values()
        .collect();
  }

  void save(JavaSparkContext sc, List<Dependency> links) {
    CassandraDependencies dependencies = new CassandraDependencies(links, System.currentTimeMillis());
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
    Set<Integer> ports = Sets.newLinkedHashSet();
    for (String contactPoint : contactPoints.split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      ports.add(parsed.getPortOrDefault(9042));
    }
    return ports.size() == 1 ? String.valueOf(ports.iterator().next()) : "9042";
  }

  /** Added so the code is compilable against scala 2.10 (used in spark 1.6.2) */
  private static <T1, T2> Tuple2<T1, T2> tuple2(T1 v1, T2 v2) {
    return new Tuple2<>(v1, v2); // in scala 2.11+ Tuple.apply works naturally
  }

  static void checkNoNull(String msg, Object object) {
    if (object == null) {
      throw new NullPointerException(String.format("%s is null", msg));
    }
  }

  public static long midnightUTC(long epochMillis) {
    Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    day.setTimeInMillis(epochMillis);
    day.set(Calendar.MILLISECOND, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.HOUR_OF_DAY, 0);
    return day.getTimeInMillis();
  }

  /**
   * DTO object used to store dependencies to Cassandra, see {@link com.datastax.spark.connector.mapper.JavaBeanColumnMapper}
   */
  public final static class CassandraDependencies implements Serializable {
    private static final long serialVersionUID = 0L;

    private List<Dependency> dependencies;
    private long ts;

    public CassandraDependencies(List<Dependency> dependencies, long ts) {
      this.dependencies = dependencies;
      this.ts = ts;
    }

    public List<Dependency> getDependencies() {
      return dependencies;
    }

    public Long getTs() {
      return ts;
    }

    public Long getTsIndex() {
      return ts;
    }
  }
}

