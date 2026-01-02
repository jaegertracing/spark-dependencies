/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import org.testcontainers.containers.GenericContainer;

/**
 * @author Danish Siddiqui
 */
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class OpenSearchDependenciesDockerJobTest extends OpenSearchDependenciesJobTest {
  private static String dependenciesJobTag() {
    String tag = System.getenv("SPARK_DEPENDENCIES_JOB_IMAGE_TAG");
    if (tag == null || tag.trim().isEmpty()) {
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
    dependenciesJob = OpenSearchDependenciesJob.builder()
        .nodes("http://" + jaegerOpenSearchEnvironment.getOpenSearchIPPort())
        .day(java.time.LocalDate.now())
        .build();

    try {
      jaegerOpenSearchEnvironment.refresh();
      // Wait a bit to ensure all spans are fully indexed and visible
      Thread.sleep(2000);
    } catch (java.io.IOException e) {
      throw new RuntimeException("Could not refresh OpenSearch", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting", e);
    }

    // Use the same date as the test - format it as ISO-8601 date string for the
    // DATE env var
    String dateStr = java.time.LocalDate.now().toString();

    System.out
        .println("Running Docker spark-dependencies job with DATE=" + dateStr + ", OS_NODES=http://opensearch:9200");
    System.out.println("::group::🚧 🚧 🚧 OpenSearchDependenciesDockerJob logs");
    try (GenericContainer<?> sparkDependenciesJob = new GenericContainer<>(
        DockerImageName.parse("ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:" + dependenciesJobTag()))
        .withNetwork(jaegerOpenSearchEnvironment.network)
        .withLogConsumer(new LogToConsolePrinter("[spark-dependencies] "))
        .withEnv("STORAGE", "opensearch")
        .withEnv("OS_NODES", "http://opensearch:9200")
        .withEnv("DATE", dateStr)
        .withEnv("JAVA_OPTS",
            "--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED -Djdk.reflect.useDirectMethodHandle=false")
        .dependsOn(jaegerOpenSearchEnvironment.opensearch, jaegerOpenSearchEnvironment.jaegerAll)) {
      sparkDependenciesJob.start();
      await("spark-dependencies-job execution")
          .atMost(3, TimeUnit.MINUTES)
          .until(() -> !sparkDependenciesJob.isRunning());

      Long exitCode = sparkDependenciesJob.getCurrentContainerInfo()
          .getState()
          .getExitCodeLong();

      if (exitCode != null && exitCode != 0) {
        throw new RuntimeException("Spark dependencies job failed with exit code: " + exitCode);
      }
    } finally {
      System.out.println("::endgroup::");
    }

    try {
      jaegerOpenSearchEnvironment.refresh();
    } catch (java.io.IOException e) {
      throw new RuntimeException("Could not refresh OpenSearch", e);
    }
  }
}
