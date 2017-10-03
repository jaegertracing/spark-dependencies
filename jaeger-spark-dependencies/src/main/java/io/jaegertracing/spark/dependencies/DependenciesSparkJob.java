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
import java.time.LocalDate;

public final class DependenciesSparkJob {

  public static void main(String[] args) throws UnsupportedEncodingException {
    String storage = System.getenv("STORAGE");
    if (storage == null) {
      throw new IllegalArgumentException("Missing environmental variable STORAGE");
    }

    LocalDate date = LocalDate.now();
    if (args.length == 1) {
      date = parseZonedDateTime(args[0]);
    } else if (System.getenv("DATE") != null) {
      date = parseZonedDateTime(System.getenv("DATE"));
    }

    run(storage, date);
  }

  private static void run(String storage, LocalDate localDate) throws UnsupportedEncodingException {
    String jarPath = pathToUberJar();
    if ("elasticsearch".equalsIgnoreCase(storage)) {
      ElasticsearchDependenciesJob.builder()
          .jars(jarPath)
          .day(localDate)
          .build()
          .run();
    } else if ("cassandra".equalsIgnoreCase(storage)) {
      CassandraDependenciesJob.builder()
          .jars(jarPath)
          .day(localDate)
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

  static LocalDate parseZonedDateTime(String date) {
    return LocalDate.parse(date);
  }
}
