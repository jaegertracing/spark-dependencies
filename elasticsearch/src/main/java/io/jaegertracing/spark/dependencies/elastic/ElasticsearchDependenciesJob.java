package io.jaegertracing.spark.dependencies.elastic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.DependencyLinks;
import io.jaegertracing.spark.dependencies.model.Dependencies;
import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJob {

  private static final Logger log = Logger.getLogger(ElasticsearchDependenciesJob.class.getName());

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    String index = getEnv("ES_INDEX", "jaeger");
    String hosts = getEnv("ES_HOSTS", "127.0.0.1");
    String username = getEnv("ES_USERNAME", null);
    String password = getEnv("ES_PASSWORD", null);

    final Map<String, String> sparkProperties = new LinkedHashMap<>();

    Builder() {
      sparkProperties.put("spark.ui.enabled", "false");
      // don't die if there are no spans
      sparkProperties.put("es.index.read.missing.as.empty", "true");
      sparkProperties.put("es.nodes.wan.only", getEnv("ES_NODES_WAN_ONLY", "false"));
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
    String sparkMaster = getEnv("SPARK_MASTER", "local[*]");
    // needed when not in local mode
    String[] jars;
    Runnable logInitializer;

    // By default the job only works on traces whose first timestamp is today
    long day = midnightUTC(System.currentTimeMillis());

    /** When set, this indicates which jars to distribute to the cluster. */
    public Builder jars(String... jars) {
      this.jars = jars;
      return this;
    }

    /** The index prefix to use when generating daily index names. Defaults to "zipkin" */
    public Builder index(String index) {
      checkNoTNull("index", index);
      this.index = index;
      return this;
    }

    public Builder hosts(String hosts) {
      checkNoTNull(hosts, "hosts");
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

    /** Day (in epoch milliseconds) to process dependencies for. Defaults to today. */
    public Builder day(long day) {
      this.day = midnightUTC(day);
      return this;
    }

    /** Ensures that logging is setup. Particularly important when in cluster mode. */
    public Builder logInitializer(Runnable logInitializer) {
      checkNoTNull("logInitializer", logInitializer);
      this.logInitializer = logInitializer;
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

  final String index;
  final long day;
  final String dateStamp;
  final SparkConf conf;
  final Runnable logInitializer;

  ElasticsearchDependenciesJob(Builder builder) {
    this.index = builder.index;
    this.day = builder.day;
    String dateSeparator = getEnv("ES_DATE_SEPARATOR", "-");
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd".replace("-", dateSeparator));
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.dateStamp = df.format(new Date(builder.day));
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
    conf.set("es.nodes", parseHosts(builder.hosts));
    if (builder.hosts.indexOf("https") != -1) {
      conf.set("es.net.ssl", "true");
    }
    for (Map.Entry<String, String> entry : builder.sparkProperties.entrySet()) {
      conf.set(entry.getKey(), entry.getValue());
    }
    this.logInitializer = builder.logInitializer;
  }

  public void run() {
    run( // multi-type index
        "jaeger-span-" + dateStamp,
        "jaeger-dependencies-" + dateStamp + "/dependencies"
    );

    log.info("Done");
  }

  void run(String spanResource, String depResource) {
    JavaSparkContext sc = new JavaSparkContext(conf);

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    try {
      JavaPairRDD<String, Iterable<Span>> traces = JavaEsSpark
          .esJsonRDD(sc, spanResource)
          .map(tuple -> {
            System.out.println(tuple._1());
            System.out.println(tuple._2());
            Span span = objectMapper.readValue(tuple._2(), Span.class);
            System.out.println("TraceId >> " + span.getTraceIdArr().toString());
            System.out.println("SpanId >> " + span.getSpanId());
            return span;
          }).groupBy(v1 -> {
            return v1.getTraceIdArr();
          });

      List<Dependency> dependencyLinks = traces.flatMapValues(DependencyLinks.dependencyLinks()).values()
          .mapToPair(dependency -> tuple2(tuple2(dependency.getParent(), dependency.getChild()),
              dependency))
          .reduceByKey((link1, link2) ->
              new Dependency(link1.getParent(), link2.getChild(),
                  link1.getCallCount() + link2.getCallCount()))
          .values()
          .collect();

      System.out.println(dependencyLinks);
      store(sc, dependencyLinks, depResource);
    } finally {
      sc.stop();
    }
  }

  private void store(JavaSparkContext javaSparkContext, List<Dependency> dependencyLinks,
      String resource) {
    if (dependencyLinks.isEmpty()) {
      return;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    String json;
    try {
      json =objectMapper.writeValueAsString(new Dependencies(dependencyLinks, System.currentTimeMillis()));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialize dependencies", e);
    }

    System.out.println(json);
    JavaEsSpark.saveJsonToEs(javaSparkContext.parallelize(Collections.singletonList(json)), resource);
  }

  /** Added so the code is compilable against scala 2.10 (used in spark 1.6.2) */
  private static <T1, T2> Tuple2<T1, T2> tuple2(T1 v1, T2 v2) {
    return new Tuple2<>(v1, v2); // in scala 2.11+ Tuple.apply works naturally
  }

  static void checkNoTNull(String msg, Object object) {
    if (object == null) {
      throw new NullPointerException(String.format("%s is null", msg));
    }
  }
  private static String getEnv(String key, String defaultValue) {
    String result = System.getenv(key);
    return result != null && !result.isEmpty() ? result : defaultValue;
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
  static String parseHosts(String hosts) {
    StringBuilder to = new StringBuilder();
    String[] hostParts = hosts.split(",");
    for (int i = 0; i < hostParts.length; i++) {
      String host = hostParts[i];
      if (host.startsWith("http")) {
        URI httpUri = URI.create(host);
        int port = httpUri.getPort();
        if (port == -1) {
          port = host.startsWith("https") ? 443 : 80;
        }
        to.append(httpUri.getHost() + ":" + port);
      } else {
        to.append(host);
      }
      if (i + 1 < hostParts.length) {
        to.append(',');
      }
    }
    return to.toString();
  }
}
