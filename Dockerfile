#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#

# Simple runtime image that receives a pre-built JAR from the host
FROM eclipse-temurin:21.0.9_10-jre@sha256:d45e4719da1774b3abde398867f78a6d9b9afe4265f574f3d54062f6daeb00b3
LABEL org.opencontainers.image.authors="The Jaeger Authors <cncf-jaeger-maintainers@lists.cncf.io>"

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
