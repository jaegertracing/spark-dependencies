#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#

# Simple runtime image that receives a pre-built JAR from the host
FROM eclipse-temurin:21.0.12_8-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
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
