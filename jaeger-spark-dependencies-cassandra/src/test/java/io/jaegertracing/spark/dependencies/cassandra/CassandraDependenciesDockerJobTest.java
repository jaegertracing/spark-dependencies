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
package io.jaegertracing.spark.dependencies.cassandra;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class CassandraDependenciesDockerJobTest extends CassandraDependenciesJobTest {
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
    System.out.println("::group::🚧 🚧 🚧 CassandraDependenciesDockerJob logs");
    try (GenericContainer<?> sparkDependenciesJob = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:" + dependenciesJobTag()))
            .withNetwork(network)
            .withLogConsumer(new LogToConsolePrinter("[spark-dependencies] "))
            .withEnv("CASSANDRA_KEYSPACE", "jaeger_v1_dc1")
            .withEnv("CASSANDRA_CONTACT_POINTS", "cassandra") // This should be an address within the docker network
            .withEnv("CASSANDRA_LOCAL_DC", cassandra.getLocalDatacenter())
            .withEnv("CASSANDRA_USERNAME", cassandra.getUsername())
            .withEnv("CASSANDRA_PASSWORD", cassandra.getPassword())
            .dependsOn(cassandra, jaegerCassandraSchema);){
      sparkDependenciesJob.start();
      await("spark-dependencies-job execution")
              .atMost(3, TimeUnit.MINUTES)
              .until(() -> !sparkDependenciesJob.isRunning());
    } finally {
        System.out.println("::endgroup::");
    }
  }
}
