# Jaeger Spark dependencies

This is a Spark job that will collect spans from your datastore, analyze links between services,
and store them for later presentation in the UI.

This job parses all traces in the current day in UTC time. This means you should schedule it to run
just prior to midnight UTC.

## Quick-start

```bash
$ wget -O zipkin-dependencies.jar 'https://search.maven.org/remote_content?g=io.zipkin.dependencies&a=zipkin-dependencies&v=LATEST'
$ STORAGE_TYPE=cassandra java -jar zipkin-dependencies.jar
```

You can also start Jaeger Dependencies via [Docker](https://github.com/openzipkin/docker-zipkin-dependencies).
```bash
$ docker run --env STORAGE_TYPE=cassandra --env CASSANDRA_CONTACT_POINTS=host1,host2 openzipkin/zipkin-dependencies
```

## Usage

By default, this job parses all traces since midnight UTC. You can parse traces for a different day
via an argument in YYYY-mm-dd format, like 2016-07-16.

```bash
# ex to run the job to process yesterday's traces on OS/X
$ STORAGE_TYPE=cassandra java -jar zipkin-dependencies.jar `date -uv-1d +%F`
# or on Linux
$ STORAGE_TYPE=cassandra java -jar zipkin-dependencies.jar `date -u -d '1 day ago' +%F`
```

## Environment Variables
`zipkin-dependencies` applies configuration parameters through environment variables. At the
moment, separate binaries are made for each storage layer.

The following variables are common to all storage layers:
    * `SPARK_MASTER`: Spark master to submit the job to; Defaults to `local[*]`
    * `JAEGER_LOG_LEVEL`: Log level for jaeger-related status; Defaults to INFO (use DEBUG for details)

### Cassandra
Cassandra is used when `STORAGE_TYPE=cassandra`.

    * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin".
    * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster. Defaults to localhost
    * `CASSANDRA_LOCAL_DC`: The local DC to connect to (other nodes will be ignored)
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails
    * `CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

Example usage:

```bash
$ STORAGE_TYPE=cassandra CASSANDRA_USERNAME=user CASSANDRA_PASSWORD=pass java -jar jaeager-dependencies.jar
```

## Building locally

To build the job from source and run against a local cassandra, in Spark's standalone mode.

```bash
# Build the spark jobs
$ ./mvnw -DskipTests clean install
$ STORAGE_TYPE=cassandra java -jar ./main/target/zipkin-dependencies*.jar
```

## Troubleshooting

When troubleshooting, always set `JAEGER_LOG_LEVEL=DEBUG` as this output
is important when figuring out why a trace didn't result in a link.

If you set `SPARK_MASTER` to something besides local, remember that log
output also ends up in `stderr` of the workers.
