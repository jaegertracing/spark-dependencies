/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies;

import io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob;
import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import java.io.UnsupportedEncodingException;
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
    String peerServiceTag = System.getenv("PEER_SERVICE_TAG");
    if (peerServiceTag == null){
      peerServiceTag = "peer.service";
    }
    String jarPath = Utils.pathToUberJar(DependenciesSparkJob.class);
    if ("elasticsearch".equalsIgnoreCase(storage)) {
      ElasticsearchDependenciesJob.builder()
          .jars(jarPath)
          .day(localDate)
          .build()
          .run(peerServiceTag);
    } else if ("cassandra".equalsIgnoreCase(storage)) {
      CassandraDependenciesJob.builder()
          .jars(jarPath)
          .day(localDate)
          .build()
          .run(peerServiceTag);
    } else {
      throw new IllegalArgumentException("Unsupported storage: " + storage);
    }
  }

  static LocalDate parseZonedDateTime(String date) {
    return LocalDate.parse(date);
  }
}
