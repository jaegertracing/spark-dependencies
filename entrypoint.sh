#!/bin/sh
#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#


# Taken from https://github.com/radanalyticsio/openshift-spark/blob/2.4/modules/common/added/scripts/entrypoint#L50
# OpenShift passes random UID and spark requires it to be present in /etc/passwd
patch_uid() {
    # Check whether there is a passwd entry for the container UID
    myuid=$(id -u)
    mygid=$(id -g)
    uidentry=$(getent passwd $myuid)

    # If there is no passwd entry for the container UID, attempt to create one
    if [ -z "$uidentry" ] ; then
        if [ -w /etc/passwd ] ; then
            echo "$myuid:x:$myuid:$mygid:anonymous uid:${PWD}:/bin/false" >> /etc/passwd
        else
            echo "Container ENTRYPOINT failed to add passwd entry for anonymous UID"
        fi
    fi
}

patch_uid

# Use the single JAR name
JAR_PATH="$APP_HOME/app.jar"

# Determine main class based on VARIANT_TYPE
if [ "$VARIANT_TYPE" = "cassandra" ]; then
    MAIN_CLASS="io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob"
elif [ -n "$VARIANT_TYPE" ] && [ "${VARIANT_TYPE#elasticsearch}" != "$VARIANT_TYPE" ]; then
    # VARIANT_TYPE starts with "elasticsearch"
    MAIN_CLASS="io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob"
elif [ "$VARIANT_TYPE" = "opensearch" ]; then
    MAIN_CLASS="io.jaegertracing.spark.dependencies.opensearch.OpenSearchDependenciesJob"
else
    # Fallback to unified JAR (for backward compatibility or local builds)
    MAIN_CLASS="io.jaegertracing.spark.dependencies.DependenciesSparkJob"
fi

# Set default Log4j2 StatusLogger level if not already set
# This suppresses Log4j2 StatusLogger errors triggered by OpenSearch's programmatic logging configuration
# Users can override this by setting the LOG4J_STATUS_LOGGER_LEVEL environment variable
LOG4J_STATUS_LOGGER_LEVEL="${LOG4J_STATUS_LOGGER_LEVEL:-OFF}"

# Required Java module options for Spark to work with Java 21+
# These --add-opens flags are necessary for Spark to access internal Java APIs
SPARK_JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
--add-opens=java.base/sun.security.action=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
-Djdk.reflect.useDirectMethodHandle=false"

# Execute the job with the determined main class
# SPARK_JAVA_OPTS come first (required for Spark), then JAVA_OPTS (user customizations), then Log4j config
exec java ${SPARK_JAVA_OPTS} ${JAVA_OPTS} -Dorg.apache.logging.log4j.simplelog.StatusLogger.level=${LOG4J_STATUS_LOGGER_LEVEL} -cp "$JAR_PATH" "$MAIN_CLASS" "$@"
