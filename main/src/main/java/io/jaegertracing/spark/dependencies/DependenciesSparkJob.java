package io.jaegertracing.spark.dependencies;

import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob;

public final class DependenciesSparkJob {
  /** Runs with defaults, starting today */
  public static void main(String[] args) throws UnsupportedEncodingException {
    String jarPath = pathToUberJar();
    long day = args.length == 1 ? parseDay(args[0]) : System.currentTimeMillis();

    String jaegerLogLevel = System.getenv("JAEGER_LOG_LEVEL");
    if (jaegerLogLevel == null) jaegerLogLevel = "INFO";
    Runnable logInitializer = LogInitializer.create(jaegerLogLevel);
    logInitializer.run(); // Ensures local log commands emit

    if (System.getenv("STORAGE").equalsIgnoreCase("elasticsearch")) {
      ElasticsearchDependenciesJob.builder()
          .logInitializer(logInitializer)
          .jars(jarPath)
          .day(day)
          .build()
          .run();
    } else {
      CassandraDependenciesJob.builder()
          .logInitializer(logInitializer)
          .jars(jarPath)
          .day(day)
          .build()
          .run();
    }
  }

  static String pathToUberJar() throws UnsupportedEncodingException {
    URL jarFile = DependenciesSparkJob.class.getProtectionDomain().getCodeSource().getLocation();
    return URLDecoder.decode(jarFile.getPath(), "UTF-8");
  }

  static long parseDay(String formattedDate) {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      return df.parse(formattedDate).getTime();
    } catch (ParseException e) {
      throw new IllegalArgumentException(
          "First argument must be a yyyy-MM-dd formatted date. Ex. 2016-07-16");
    }
  }
}
