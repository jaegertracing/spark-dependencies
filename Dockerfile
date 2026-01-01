#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#

# Simple runtime image that receives a pre-built JAR from the host
FROM apache/spark:3.5.0

# Build argument to specify the variant type
# Supported values: cassandra, elasticsearch7, elasticsearch8, elasticsearch9
ARG VARIANT=elasticsearch9

# Switch to root to perform setup, then switch back to spark user
USER root

ENV APP_HOME=/app/
ENV VARIANT_TYPE=${VARIANT}

# The JAR is provided by the GHA runner into the target/ folder
COPY target/*.jar $APP_HOME/app.jar

WORKDIR $APP_HOME

COPY entrypoint.sh /

RUN chgrp root /etc/passwd && chmod g+rw /etc/passwd

USER spark

ENTRYPOINT ["/entrypoint.sh"]
