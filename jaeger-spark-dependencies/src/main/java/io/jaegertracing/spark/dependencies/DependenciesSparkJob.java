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
