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

# Execute the job with the determined main class
exec java ${JAVA_OPTS} -cp "$JAR_PATH" "$MAIN_CLASS" "$@"
