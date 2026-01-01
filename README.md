[![Latest image](https://ghcr-badge.egpl.dev/jaegertracing/spark-dependencies/spark-dependencies/latest_tag?trim=major&label=latest)](https://github.com/jaegertracing/spark-dependencies/pkgs/container/spark-dependencies%2Fspark-dependencies)

# Jaeger Spark dependencies
This is a Spark job that collects spans from storage, analyze links between services,
and stores them for later presentation in the UI. Note that it is needed for the production deployment.
`all-in-one` distribution does not need this job.

This job parses all traces on a given day, based on UTC. By default, it processes the current day,
but other days can be explicitly specified.

## Quick-start
Spark job can be run as docker container and also as java executable:

### Container Image Variants

Starting with version 0.6.x, Docker images are published with variant-specific tags. **Each variant automatically uses the appropriate storage backend, so the `STORAGE` environment variable is no longer needed.**

The images are named `ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:{VERSION}-{VARIANT}`:

- **`VERSION-cassandra`**: For Cassandra storage (uses CassandraDependenciesJob directly)
- **`VERSION-elasticsearch7`**: For Elasticsearch 7.12-7.16 (uses ElasticsearchDependenciesJob with ES connector 7.17.29)
- **`VERSION-elasticsearch8`**: For Elasticsearch 7.17+ and 8.x (uses ElasticsearchDependenciesJob with ES connector 8.13.4)
- **`VERSION-elasticsearch9`**: For Elasticsearch 9.x (uses ElasticsearchDependenciesJob with ES connector 9.1.3) - also tagged as `:latest`

Example for Cassandra:
```bash
$ docker run \
  --env CASSANDRA_CONTACT_POINTS=host1,host2 \
  ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:v0.5.3-cassandra
```

Example for Elasticsearch 8.x:
```bash
$ docker run \
  --env ES_NODES=http://elasticsearch:9200 \
  ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:v0.5.3-elasticsearch8
```

Use `--env JAVA_OPTS=-Djavax.net.ssl.` to set trust store and other Java properties.

Note: the latest versions are hosted on `ghcr.io`, not on Docker Hub.

As jar file:
```bash
STORAGE=cassandra java -jar jaeger-spark-dependencies.jar
```

## Usage
By default, this job parses all traces since midnight UTC. You can parse traces for a different day
via an argument in YYYY-mm-dd format, like 2016-07-16 or specify the date via an env property.

```bash
# ex to run the job to process yesterday's traces on OS/X
$ STORAGE=cassandra java -jar jaeger-spark-dependencies.jar `date -uv-1d +%F`
# or on Linux
$ STORAGE=cassandra java -jar jaeger-spark-dependencies.jar `date -u -d '1 day ago' +%F`
```

### Configuration
`jaeger-spark-dependencies` applies configuration parameters through environment variables.

The following variables are common to all storage layers:

* `SPARK_MASTER`: Spark master to submit the job to; Defaults to `local[*]`
* `DATE`: Date in YYYY-mm-dd format. Denotes a day for which dependency links will be created.
* `PEER_SERVICE_TAG`: Tag name used to identify peer service in spans. Defaults to `peer.service`

### Cassandra
Cassandra is used when `STORAGE=cassandra`.

* `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "jaeger_v1_dc1".
* `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster.
  Defaults to localhost
* `CASSANDRA_LOCAL_DC`: The local DC to connect to (other nodes will be ignored)
* `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception
  on startup if authentication fails
* `CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`,
  Defaults to false.
* `CASSANDRA_CLIENT_AUTH_ENABLED`: If set enables client authentication on SSL connections.
  Requires `javax.net.ssl.keyStore` and `javax.net.ssl.keyStorePassword`, defaults to false.

Example usage:

```bash
$ STORAGE=cassandra CASSANDRA_CONTACT_POINTS=localhost:9042 java -jar jaeger-spark-dependencies.jar
```
### Elasticsearch
Elasticsearch is used when `STORAGE=elasticsearch`.

**Important**: Use the appropriate Docker image variant for your Elasticsearch version:
- ES 7.12-7.16: Use `:VERSION-elasticsearch7` tag
- ES 7.17-8.x: Use `:VERSION-elasticsearch8` tag  
- ES 9.x: Use `:VERSION-elasticsearch9` tag (or `:latest`)

#### Configuration

* `ES_NODES`: A comma separated list of elasticsearch hosts advertising http. Defaults to
  127.0.0.1. Add port section if not listening on port 9200. Only one of these hosts
  needs to be available to fetch the remaining nodes in the cluster. It is
  recommended to set this to all the master nodes of the cluster. Use url format for
  SSL. For example, "https://yourhost:8888"
* `ES_NODES_WAN_ONLY`: Set to true to only use the values set in ES_NODES, for example if your
  elasticsearch cluster is in Docker. If you're using a cloudprovider
  such as AWS Elasticsearch, set this to true. Defaults to false
* `ES_USERNAME` and `ES_PASSWORD`: Elasticsearch basic authentication. Use when X-Pack security
  (formerly Shield) is in place. By default no username or password is provided to elasticsearch.
* `ES_CLIENT_NODE_ONLY`: Set to true to disable elasticsearch cluster nodes.discovery and enable nodes.client.only.
  If your elasticsearch cluster's data nodes only listen on loopback ip, set this to true.
  Defaults to false.
* `ES_INDEX_PREFIX`: index prefix of Jaeger indices. By default unset.
* `ES_INDEX_DATE_SEPARATOR`: index date separator of Jaeger indices. The default value is `-`.
  For example `.` will find index "jaeger-span-2020.11.25".
* `ES_TIME_RANGE`: How far in the past the job should look to for spans, the maximum and default is `24h`.
  Any value accepted by [date-math](https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math) can be used here, but the anchor is always `now`.
* `ES_USE_ALIASES`: Set to true to use index alias names to read from and write to.
  Usually required when using rollover indices.

Example usage:

```bash
$ STORAGE=elasticsearch ES_NODES=http://localhost:9200 java -jar jaeger-spark-dependencies.jar
```

## Design

At a high-level, this job does the following:

* read lots of spans from a time period
* group them by traceId
* construct a graph using parent-child relationships expressed in span references
* for each edge `(parent span, child span)` output `(parent service, child service, count)`
* write the results to the database (e.g. `dependencies_v2` table in [Cassandra](https://github.com/jaegertracing/jaeger/blob/12e44faabf10cdd866391b78933eec5d6ac50fa9/plugin/storage/cassandra/schema/v004.cql.tmpl#L186))

## Building locally
To build the job locally and run tests:
```bash
./mvnw clean install # if failed add SPARK_LOCAL_IP=127.0.0.1
```

To run the unified jar (includes both Cassandra and Elasticsearch):
```bash
STORAGE=cassandra java -jar jaeger-spark-dependencies/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar
# or
STORAGE=elasticsearch ES_NODES=http://localhost:9200 java -jar jaeger-spark-dependencies/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar
```

To run storage-specific jars directly (without STORAGE variable):
```bash
# Cassandra
java -jar jaeger-spark-dependencies-cassandra/target/jaeger-spark-dependencies-cassandra-0.0.1-SNAPSHOT.jar
# Elasticsearch
ES_NODES=http://localhost:9200 java -jar jaeger-spark-dependencies-elasticsearch/target/jaeger-spark-dependencies-elasticsearch-0.0.1-SNAPSHOT.jar
```

To build Docker image:

**Note:** The Dockerfile now requires a pre-built JAR. First build the JAR using Maven, then build the Docker image.

For Cassandra:
```bash
./mvnw clean package --batch-mode -Dlicense.skip=true -DskipTests -pl jaeger-spark-dependencies-cassandra -am
mkdir -p target
cp jaeger-spark-dependencies-cassandra/target/jaeger-spark-dependencies-cassandra-0.0.1-SNAPSHOT.jar target/
docker build --build-arg VARIANT=cassandra -t jaegertracing/spark-dependencies:cassandra .
```

For Elasticsearch 9:
```bash
./mvnw clean package --batch-mode -Dlicense.skip=true -DskipTests -Dversion.elasticsearch.spark=9.1.3 -pl jaeger-spark-dependencies-elasticsearch -am
mkdir -p target
cp jaeger-spark-dependencies-elasticsearch/target/jaeger-spark-dependencies-elasticsearch-0.0.1-SNAPSHOT.jar target/
docker build --build-arg VARIANT=elasticsearch9 -t jaegertracing/spark-dependencies:elasticsearch9 .
```

In tests it's possible to specify version of Jaeger images by env variable `JAEGER_VERSION`
or system property `jaeger.version`. By default tests are using latest images.

## Running Integration Tests

The integration tests validate the Spark dependencies job against different storage backends:
- Cassandra 4.x
- Elasticsearch 7
- Elasticsearch 8
- Elasticsearch 9

### Prerequisites

Before running integration tests, ensure you have the following installed:

- **Java 21** (Temurin distribution recommended)
- **Docker** (for building images and running testcontainers)
- **Maven** (included via `./mvnw` wrapper)

### Quick Start

Use the following make targets to run integration tests:

```bash
make e2e-cassandra  # Run Cassandra integration tests
make e2e-es7        # Run Elasticsearch 7 integration tests
make e2e-es8        # Run Elasticsearch 8 integration tests
make e2e-es9        # Run Elasticsearch 9 integration tests
```

### What Each Target Does

Each test suite performs two steps:
1. Builds a Docker image with the appropriate storage variant
2. Runs tests using testcontainers against that variant

### Environment Variables

The following environment variables are used in integration tests:

- `SPARK_DEPENDENCIES_JOB_TAG`: Specifies the Docker image tag to use in tests (e.g., `test-cassandra`, `test-es7`, `test-es8`, `test-es9`)
- `ELASTICSEARCH_VERSION`: Specifies the Elasticsearch version for testcontainers to use
- `JAEGER_VERSION`: (Optional) Specifies the version of Jaeger images to use in tests. Defaults to latest.

You can also set this as a system property:
```bash
./mvnw test -Djaeger.version=2.14.0
```

### Troubleshooting

#### Docker Permission Issues
If you encounter Docker permission issues, ensure your user is in the `docker` group:
```bash
sudo usermod -aG docker $USER
```
Then log out and log back in.

#### Testcontainers Issues
If testcontainers fail to start, ensure:
1. Docker is running and accessible
2. The Ryuk image is pulled: `docker pull testcontainersofficial/ryuk:latest`
3. You have sufficient disk space for Docker images

#### Build Failures
If you encounter build failures:
1. Ensure you have Java 21 installed
2. Clean the Maven cache: `./mvnw clean`
3. Try running with the `-U` flag to force update dependencies: `./mvnw -U clean install`

#### Port Conflicts
If tests fail due to port conflicts, ensure no other services are running on the ports used by testcontainers (typically ephemeral ports, but sometimes standard ports like 9042 for Cassandra or 9200 for Elasticsearch).

## CI/CD Pipeline

The project uses a unified CI/CD pipeline (`.github/workflows/ci-cd.yml`) that implements a **Host-Build Matrix Pattern**:

1. **Build JARs** - Builds storage-specific JARs on the GitHub runner (parallel for all variants)
2. **E2E Tests** - Tests each variant using Docker containers with pre-built JARs
3. **Publish** - Publishes multi-arch Docker images (linux/amd64, linux/arm64) to GitHub Container Registry

The pipeline supports four variants:
- `cassandra` - For Cassandra storage
- `elasticsearch7` - For Elasticsearch 7.12-7.16 (ES connector 7.17.29)
- `elasticsearch8` - For Elasticsearch 7.17+ and 8.x (ES connector 8.13.4)
- `elasticsearch9` - For Elasticsearch 9.x (ES connector 9.1.3)

This approach eliminates Maven downloads inside Docker builds and parallelizes builds across all storage variants.

## License

[Apache 2.0 License](./LICENSE).
