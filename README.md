[![Latest image](https://ghcr-badge.egpl.dev/jaegertracing/spark-dependencies/spark-dependencies/latest_tag?trim=major&label=latest)](https://github.com/jaegertracing/spark-dependencies/pkgs/container/spark-dependencies%2Fspark-dependencies)

# Jaeger Spark dependencies
This is a Spark job that collects spans from storage, analyze links between services,
and stores them for later presentation in the UI. Note that it is needed for the production deployment.
`all-in-one` distribution does not need this job.

This job parses all traces on a given day, based on UTC. By default, it processes the current day,
but other days can be explicitly specified.

## Quick-start
Spark job can be run as docker container and also as java executable:

Docker:
```bash
$ docker run \
  --env STORAGE=cassandra \
  --env CASSANDRA_CONTACT_POINTS=host1,host2 \
  ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:VERSION
```

Use `--env JAVA_OPTS=-Djavax.net.ssl.` to set trust store and other Java properties.

Note: the latest vesions are hosted on `ghcr.io`, not on Docker Hub.

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

#### Version Compatibility
The default build supports a range of Elasticsearch versions. The connector version can be customized at build time:

- **Elasticsearch 7.12-7.16**: Build with `-Dversion.elasticsearch.spark=7.17.29` (latest 7.x connector)
- **Elasticsearch 7.17+ and 8.x**: Use the default build (elasticsearch-spark version 8.13.4)
- **Elasticsearch 9.x**: Build with `-Dversion.elasticsearch.spark=9.1.3`

**Note**: 
- Elasticsearch versions 7.0-7.11 are not supported by the Spark 3.x connectors
- The project uses Spark 3.5.1, which requires elasticsearch-spark-30 connectors

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
