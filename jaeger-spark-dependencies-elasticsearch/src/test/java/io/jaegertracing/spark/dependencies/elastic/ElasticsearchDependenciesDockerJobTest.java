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
package io.jaegertracing.spark.dependencies.elastic;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class ElasticsearchDependenciesDockerJobTest extends ElasticsearchDependenciesJobTest {
  private static String dependenciesJobTag() {
      String tag = System.getenv("SPARK_DEPENDENCIES_JOB_TAG");
      if (tag == null || tag.isEmpty()) {
          throw new IllegalStateException(
              "SPARK_DEPENDENCIES_JOB_TAG environment variable is required but not set. " +
              "This variable must be set to ensure tests use the locally built Docker image.");
      }
      return tag.trim();
  }

  @Override
  protected void deriveDependencies() {
    // Create the dependenciesJob instance so that after() method can call indexDate() on it
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
    
    // Use the same date as the test - format it as ISO-8601 date string for the DATE env var
    String dateStr = java.time.LocalDate.now().toString();
    
    System.out.println("Running Docker spark-dependencies job with DATE=" + dateStr + ", ES_NODES=http://elasticsearch:9200");
    System.out.println("::group::🚧 🚧 🚧 ElasticsearchDependenciesDockerJob logs");
    try (GenericContainer<?> sparkDependenciesJob = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:" + dependenciesJobTag()))
            .withNetwork(jaegerElasticsearchEnvironment.network)
            .withLogConsumer(new LogToConsolePrinter("[spark-dependencies] "))
            .withEnv("STORAGE", "elasticsearch")
            .withEnv("ES_NODES", "http://elasticsearch:9200")
            .withEnv("DATE", dateStr)
            .dependsOn(jaegerElasticsearchEnvironment.elasticsearch, jaegerElasticsearchEnvironment.jaegerCollector)) {
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
