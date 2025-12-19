[![Latest image](https://ghcr-badge.egpl.dev/jaegertracing/spark-dependencies/spark-dependencies/latest_tag?trim=major&label=latest)](https://github.com/jaegertracing/spark-dependencies/pkgs/container/spark-dependencies%2Fspark-dependencies)

# Jaeger Spark dependencies
This is a Spark job that collects spans from storage, analyze links between services,
and stores them for later presentation in the UI. Note that it is needed for the production deployment.
`all-in-one` distribution does not need this job.

This job parses all traces on a given day, based on UTC. By default, it processes the current day,
but other days can be explicitly specified.

## Quick-start
Spark job can be run as docker container and also as java executable:

### Docker Image Variants

Starting with version 0.5.3, Docker images are published with variant-specific tags. **Each variant automatically uses the appropriate storage backend, so the `STORAGE` environment variable is no longer needed.**

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

### Cassandra
Cassandra is used when `STORAGE=cassandra`.

    * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "jaeger_v1_dc1".
    * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster. Defaults to localhost
    * `CASSANDRA_LOCAL_DC`: The local DC to connect to (other nodes will be ignored)
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails
    * `CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.
    * `CASSANDRA_CLIENT_AUTH_ENABLED`: If set enables client authentication on SSL connections. Requires `javax.net.ssl.keyStore` and `javax.net.ssl.keyStorePassword`, defaults to false.

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
                  localhost. Add port section if not listening on port 9200. Only one of these hosts
                  needs to be available to fetch the remaining nodes in the cluster. It is
                  recommended to set this to all the master nodes of the cluster. Use url format for
                  SSL. For example, "https://yourhost:8888"
    * `ES_NODES_WAN_ONLY`: Set to true to only use the values set in ES_HOSTS, for example if your
                           elasticsearch cluster is in Docker. If you're using a cloudprovider
                           such as AWS Elasticsearch, set this to true. Defaults to false
    * `ES_USERNAME` and `ES_PASSWORD`: Elasticsearch basic authentication. Use when X-Pack security
                                       (formerly Shield) is in place. By default no username or
                                       password is provided to elasticsearch.
    * `ES_CLIENT_NODE_ONLY`: Set to true to disable elasticsearch cluster nodes.discovery and enable nodes.client.only.
                             If your elasticsearch cluster's data nodes only listen on loopback ip, set this to true.
                             Defaults to false
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
STORAGE=elasticsearch ES_NODES=http://localhost:9200 java -jar jaeger-spark-dependencies/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar
docker build -t jaegertracing/spark-dependencies:latest .
```

In tests it's possible to specify version of Jaeger images by env variable `JAEGER_VERSION`
or system property `jaeger.version`. By default tests are using latest images.

## License

[Apache 2.0 License](./LICENSE).
