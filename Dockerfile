#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#

# Simple runtime image that receives a pre-built JAR from the host
FROM eclipse-temurin:21.0.9_10-jre@sha256:b0f6befb3f2af49704998c4425cb6313c1da505648a8e78cee731531996f735d

# Build argument to specify the variant type
# Supported values: cassandra, elasticsearch7, elasticsearch8, elasticsearch9
ARG VARIANT=elasticsearch9

ENV APP_HOME=/app/
ENV VARIANT_TYPE=${VARIANT}

# The JAR is provided by the GHA runner into the artifact-target folder
COPY artifact-target/jaeger-spark-dependencies*.jar $APP_HOME/app.jar

WORKDIR $APP_HOME

COPY entrypoint.sh /

RUN chgrp root /etc/passwd && chmod g+rw /etc/passwd
USER 185

ENTRYPOINT ["/entrypoint.sh"]
