/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import io.jaegertracing.spark.dependencies.LogToConsolePrinter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class ElasticsearchDependenciesDockerJobTest extends ElasticsearchDependenciesJobTest {
  private static String dependenciesJobTag() {
    String tag = System.getenv("SPARK_DEPENDENCIES_JOB_IMAGE_TAG");
    if (tag == null || tag.isEmpty()) {
      throw new IllegalStateException(
          "SPARK_DEPENDENCIES_JOB_IMAGE_TAG environment variable is required but not set. " +
              "This variable must be set to ensure tests use the locally built Docker image.");
    }
    return tag.trim();
  }

  @Override
  protected void deriveDependencies() {
    // Create the dependenciesJob instance so that after() method can call
    // indexDate() on it
    dependenciesJob = ElasticsearchDependenciesJob.builder()
        .nodes("http://" + jaegerElasticsearchEnvironment.getElasticsearchIPPort())
        .day(java.time.LocalDate.now())
        .build();

    try {
      jaegerElasticsearchEnvironment.refresh();
      // Wait a bit to ensure all spans are fully indexed and visible
      Thread.sleep(2000);
    } catch (java.io.IOException e) {
      throw new RuntimeException("Could not refresh Elasticsearch", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting", e);
    }

    // Use the same date as the test - format it as ISO-8601 date string for the
    // DATE env var
    String dateStr = java.time.LocalDate.now().toString();

    System.out
        .println("Running Docker spark-dependencies job with DATE=" + dateStr + ", ES_NODES=http://elasticsearch:9200");
    System.out.println("::group::🚧 🚧 🚧 ElasticsearchDependenciesDockerJob logs");
    try (GenericContainer<?> sparkDependenciesJob = new GenericContainer<>(
        DockerImageName.parse("ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:" + dependenciesJobTag()))
        .withNetwork(jaegerElasticsearchEnvironment.network)
        .withLogConsumer(new LogToConsolePrinter("[spark-dependencies] "))
        .withEnv("STORAGE", "elasticsearch")
        .withEnv("ES_NODES", "http://elasticsearch:9200")
        .withEnv("DATE", dateStr)
        .withEnv("JAVA_OPTS",
            "--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED -Djdk.reflect.useDirectMethodHandle=false")
        .dependsOn(jaegerElasticsearchEnvironment.elasticsearch, jaegerElasticsearchEnvironment.jaegerAll)) {
      sparkDependenciesJob.start();
      await("spark-dependencies-job execution")
          .atMost(3, TimeUnit.MINUTES)
          .until(() -> !sparkDependenciesJob.isRunning());
    } finally {
      System.out.println("::endgroup::");
    }

    try {
      jaegerElasticsearchEnvironment.refresh();
    } catch (java.io.IOException e) {
      throw new RuntimeException("Could not refresh Elasticsearch", e);
    }
  }
}
