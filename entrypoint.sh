#!/bin/sh
#
# Copyright 2017 The Jaeger Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
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
else
    # Fallback to unified JAR (for backward compatibility or local builds)
    MAIN_CLASS="io.jaegertracing.spark.dependencies.DependenciesSparkJob"
fi

# Java 17+ requires additional --add-opens arguments for Spark compatibility
JAVA17_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED \
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
exec java ${JAVA17_OPTS} ${JAVA_OPTS} -cp "$JAR_PATH" "$MAIN_CLASS" "$@"
