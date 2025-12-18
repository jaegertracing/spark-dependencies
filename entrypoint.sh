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

# If VARIANT_TYPE is set, override the command to use the specific main class
# This allows variant-specific images to bypass the unified entry point
if [ -n "$VARIANT_TYPE" ]; then
    case "$VARIANT_TYPE" in
        cassandra)
            MAIN_CLASS="io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob"
            ;;
        elasticsearch*)
            MAIN_CLASS="io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob"
            ;;
        *)
            # If unrecognized variant, fall through to default command
            exec "$@"
            exit $?
            ;;
    esac
    
    # Replace the jar execution command with the specific main class
    if [ "$1" = "java" ]; then
        shift  # Remove 'java'
        # Extract JAVA_OPTS if present
        OPTS=""
        while [ $# -gt 0 ]; do
            case "$1" in
                -jar)
                    shift  # Remove -jar
                    JAR_PATH="$1"
                    shift  # Remove jar path
                    break
                    ;;
                *)
                    OPTS="$OPTS $1"
                    shift
                    ;;
            esac
        done
        exec java $OPTS -cp "$JAR_PATH" "$MAIN_CLASS" "$@"
        exit $?
    fi
fi

exec "$@"
exit $?
