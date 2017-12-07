[![Build Status][ci-img]][ci]

# Jaeger Spark dependencies
This is a Spark job that collects spans from storage, analyze links between services,
and stores them for later presentation in the UI. Note that it is needed for the production deployment.
`all-in-one` distribution does not need this job.

This job parses all traces on a given day, based on UTC. By default, it processes the current day,
but other days can be explicitly specified.

This repository is based on [zipkin-dependencies](https://github.com/openzipkin/zipkin-dependencies).

## Quick-start
Spark job can be run as docker container and also as java executable:

Docker:
```bash
$ docker run --env STORAGE=cassandra --env CASSANDRA_CONTACT_POINTS=host1,host2 jaegertracing/spark-dependencies
```

As jar file:
```bash
STORAGE=cassandra java -jar jaeager-spark-dependencies.jar
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
    * `CASSANDRA_SSL_KEYSTORE`: If set enables client authentication on SSL connections.
    * `CASSANDRA_SSL_KEYSTORE_PASSWORD`: Password for the keystore.

Example usage:

```bash
$ STORAGE=cassandra CASSANDRA_CONTACT_POINTS=localhost:9042 java -jar jaeger-spark-dependencies.jar
```
### Elasticsearch
Elasticsearch is used when `STORAGE=elasticsearch`.

    * `ES_NODES`: A comma separated list of elasticsearch hosts advertising http. Defaults to
                  localhost. Add port section if not listening on port 9200. Only one of these hosts
                  needs to be available to fetch the remaining nodes in the cluster. It is
                  recommended to set this to all the master nodes of the cluster. Use url format for
                  SSL. For example, "https://yourhost:8888"
    * `ES_NODES_WAN_ONLY`: Set to true to only use the values set in ES_HOSTS, for example if your
                           elasticsearch cluster is in Docker. Defaults to false
    * `ES_USERNAME` and `ES_PASSWORD`: Elasticsearch basic authentication. Use when X-Pack security
                                       (formerly Shield) is in place. By default no username or
                                       password is provided to elasticsearch.

Example usage:

```bash
$ STORAGE=elasticsearch ES_NODES=http://localhost:9200 java -jar jaeger-spark-dependencies.jar
```

## Building locally
To build the job locally and run tests:
```bash
./mvnw clean install # if failed add SPARK_LOCAL_IP=127.0.0.1
docker build -t jaegertracing/spark-dependencies:latest .
```

   [ci-img]: https://travis-ci.org/jaegertracing/spark-dependencies.svg?branch=master
   [ci]: https://travis-ci.org/jaegertracing/spark-dependencies
