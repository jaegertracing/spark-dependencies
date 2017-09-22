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

import io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob;
import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public final class DependenciesSparkJob {

  public static void main(String[] args) throws UnsupportedEncodingException {
    if (args.length != 1) {
      throw new IllegalArgumentException("");
    }

    String storage = System.getenv("STORAGE");
    if (storage == null) {
      throw new IllegalArgumentException("Missing environmental variable STORAGE");
    }

    run(storage, args.length == 1 ? parseDay(args[0]) : System.currentTimeMillis());
  }

  private static void run(String storage, long day) throws UnsupportedEncodingException {
    String jarPath = pathToUberJar();
    if ("elasticsearch".equalsIgnoreCase(storage)) {
      ElasticsearchDependenciesJob.builder()
          .jars(jarPath)
          .day(day)
          .build()
          .run();
    } else if ("cassandra".equalsIgnoreCase(storage)) {
      CassandraDependenciesJob.builder()
          .jars(jarPath)
          .day(day)
          .build()
          .run();
    } else {
      throw new IllegalArgumentException("Unsupported storage: " + storage);
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
