# Integration Tests Guide

This guide provides instructions for running integration tests for the Jaeger Spark Dependencies project. These instructions are extracted from the CI workflow (`.github/workflows/ci.yaml`).

## Prerequisites

Before running integration tests, ensure you have the following installed:

- **Java 11** (Temurin distribution recommended)
- **Docker** (for building images and running testcontainers)
- **Maven** (included via `./mvnw` wrapper)

## Overview

The integration tests validate the Spark dependencies job against different storage backends:
- Cassandra 4.x
- Elasticsearch 7
- Elasticsearch 8
- Elasticsearch 9

Each test suite builds a Docker image with the appropriate storage variant and runs tests using testcontainers.

## Running Integration Tests

### 1. Cassandra Integration Tests

Tests the latest Jaeger with Cassandra 4.x storage backend.

```bash
# Build the Docker image for Cassandra variant
docker build \
  --build-arg VARIANT=cassandra \
  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-cassandra \
  .

# Run Cassandra integration tests
SPARK_DEPENDENCIES_JOB_TAG=test-cassandra \
./mvnw --batch-mode clean test -am -pl jaeger-spark-dependencies-cassandra
```

### 2. Elasticsearch 7 Integration Tests

Tests the latest Jaeger with Elasticsearch 7 storage backend.

```bash
# Build the Docker image for ES7 variant
docker build \
  --build-arg VARIANT=elasticsearch7 \
  --build-arg ELASTICSEARCH_SPARK_VERSION=7.17.10 \
  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es7 \
  .

# Compile the project with ES7 Spark connector version
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=7.17.10

# Run ES7 integration tests
SPARK_DEPENDENCIES_JOB_TAG=test-es7 \
ELASTICSEARCH_VERSION=7.3.0 \
./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=7.17.10
```

### 3. Elasticsearch 8 Integration Tests

Tests the latest Jaeger with Elasticsearch 8 storage backend.

```bash
# Build the Docker image for ES8 variant
docker build \
  --build-arg VARIANT=elasticsearch8 \
  --build-arg ELASTICSEARCH_SPARK_VERSION=8.13.4 \
  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es8 \
  .

# Compile the project with ES8 Spark connector version
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=8.13.4

# Run ES8 integration tests
SPARK_DEPENDENCIES_JOB_TAG=test-es8 \
ELASTICSEARCH_VERSION=8.3.1 \
./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=8.13.4
```

### 4. Elasticsearch 9 Integration Tests

Tests the latest Jaeger with Elasticsearch 9 storage backend. This also builds the mega-jar (unified JAR with both Cassandra and Elasticsearch support) to ensure it's tested.

```bash
# Build the Docker image for ES9 variant (unified/mega-jar)
docker build \
  --build-arg VARIANT=unified \
  --build-arg ELASTICSEARCH_SPARK_VERSION=9.1.3 \
  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es9 \
  .

# Compile the project with ES9 Spark connector version
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=9.1.3

# Run ES9 integration tests
SPARK_DEPENDENCIES_JOB_TAG=test-es9 \
ELASTICSEARCH_VERSION=9.1.3 \
./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=9.1.3
```

## Running All Tests

To run all integration tests sequentially, you can execute each test suite one after another:

```bash
# Pull Ryuk image (required for all tests)
docker pull testcontainersofficial/ryuk:0.3.0

# Run Cassandra tests
docker build --build-arg VARIANT=cassandra -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-cassandra .
./mvnw --batch-mode clean install -DskipTests
SPARK_DEPENDENCIES_JOB_TAG=test-cassandra ./mvnw --batch-mode test -pl jaeger-spark-dependencies-cassandra

# Run ES7 tests
docker build --build-arg VARIANT=elasticsearch7 --build-arg ELASTICSEARCH_SPARK_VERSION=7.17.10 -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es7 .
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=7.17.10
SPARK_DEPENDENCIES_JOB_TAG=test-es7 ELASTICSEARCH_VERSION=7.3.0 ./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=7.17.10

# Run ES8 tests
docker build --build-arg VARIANT=elasticsearch8 --build-arg ELASTICSEARCH_SPARK_VERSION=8.13.4 -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es8 .
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=8.13.4
SPARK_DEPENDENCIES_JOB_TAG=test-es8 ELASTICSEARCH_VERSION=8.3.1 ./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=8.13.4

# Run ES9 tests
docker build --build-arg VARIANT=unified --build-arg ELASTICSEARCH_SPARK_VERSION=9.1.3 -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es9 .
./mvnw --batch-mode clean install -DskipTests -Dversion.elasticsearch.spark=9.1.3
SPARK_DEPENDENCIES_JOB_TAG=test-es9 ELASTICSEARCH_VERSION=9.1.3 ./mvnw --batch-mode test -pl jaeger-spark-dependencies-elasticsearch -Dversion.elasticsearch.spark=9.1.3
```

## Environment Variables

The following environment variables are used in integration tests:

- `SPARK_DEPENDENCIES_JOB_TAG`: Specifies the Docker image tag to use in tests (e.g., `test-cassandra`, `test-es7`, `test-es8`, `test-es9`)
- `ELASTICSEARCH_VERSION`: Specifies the Elasticsearch version for testcontainers to use
- `JAEGER_VERSION`: (Optional) Specifies the version of Jaeger images to use in tests. Defaults to latest.

You can also set this as a system property:
```bash
./mvnw test -Djaeger.version=1.50.0
```

## Troubleshooting

### Docker Permission Issues
If you encounter Docker permission issues, ensure your user is in the `docker` group:
```bash
sudo usermod -aG docker $USER
```
Then log out and log back in.

### Testcontainers Issues
If testcontainers fail to start, ensure:
1. Docker is running and accessible
2. The Ryuk image is pulled: `docker pull testcontainersofficial/ryuk:0.3.0`
3. You have sufficient disk space for Docker images

### Build Failures
If you encounter build failures:
1. Ensure you have Java 11 installed
2. Clean the Maven cache: `./mvnw clean`
3. Try running with the `-U` flag to force update dependencies: `./mvnw -U clean install`

### Port Conflicts
If tests fail due to port conflicts, ensure no other services are running on the ports used by testcontainers (typically ephemeral ports, but sometimes standard ports like 9042 for Cassandra or 9200 for Elasticsearch).

## Additional Resources

- [CI Workflow](.github/workflows/ci.yaml) - The source of these instructions
- [README.md](README.md) - General project documentation
- [Testcontainers Documentation](https://www.testcontainers.org/) - Learn more about testcontainers
