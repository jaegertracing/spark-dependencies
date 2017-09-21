package io.jaegertracing.spark.dependencies.elastic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.jaegertracing.spark.dependencies.DepenencyLinksSparkJob;
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

  private final String index;
  private final long day;
  private final String dateStamp;
  private final SparkConf conf;
  private final Runnable logInitializer;

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
    run("jaeger-span-" + dateStamp,"jaeger-dependencies-" + dateStamp + "/dependencies");
    log.info("Done");
  }

  void run(String spanResource, String depResource) {
    JavaSparkContext sc = new JavaSparkContext(conf);
    try {
      JavaPairRDD<String, Iterable<Span>> traces = JavaEsSpark.esJsonRDD(sc, spanResource)
          .map(new ElasticTupleToSpan())
          .groupBy(Span::getTraceId);

      traces.foreach(stringIterableTuple2 -> {
        System.out.println(stringIterableTuple2._1() +"  ->  " +  Lists.newArrayList(stringIterableTuple2._2()).size());
      });

      List<Dependency> dependencyLinks = DepenencyLinksSparkJob.derive(traces);
      store(sc, dependencyLinks, depResource);
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
      json = objectMapper.writeValueAsString(new ElasticsearchDependencies(dependencyLinks, System.currentTimeMillis()));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialize dependencies", e);
    }

    JavaEsSpark.saveJsonToEs(javaSparkContext.parallelize(Collections.singletonList(json)), resource);
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

  public static class ElasticsearchDependencies {
    private static final long serialVersionUID = 0L;

    private List<Dependency> dependencies;
    private long ts;

    public ElasticsearchDependencies(List<Dependency> dependencies, long ts) {
      this.dependencies = dependencies;
      this.ts = ts;
    }

    public List<Dependency> getDependencies() {
      return dependencies;
    }

    public String getTimestamp() {
      // Jaeger ES dependency storage uses RFC3339Nano for timestamp
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .format(new Date(ts)).toString();
    }
  }
}
