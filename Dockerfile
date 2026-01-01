#
# Copyright (c) The Jaeger Authors
# SPDX-License-Identifier: Apache-2.0
#

FROM eclipse-temurin:21@sha256:cd772abe6bc42ddc2f5927756ea33fb26470726438fe0631472cccd4c5ecc304 AS builder

# Build argument to specify the variant type
# Supported values: cassandra, elasticsearch7, elasticsearch8, elasticsearch9, unified
ARG VARIANT=elasticsearch9

# Build argument to specify elasticsearch-spark version (only used for elasticsearch variants)
# Supported values: 7.17.29 (ES 7.12-7.16), 8.13.4 (ES 7.17+/8.x), 9.1.3 (ES 9.x)
ARG ELASTICSEARCH_SPARK_VERSION=9.1.3

ENV APP_HOME=/app/

COPY pom.xml $APP_HOME
COPY jaeger-spark-dependencies $APP_HOME/jaeger-spark-dependencies
COPY jaeger-spark-dependencies-cassandra $APP_HOME/jaeger-spark-dependencies-cassandra
COPY jaeger-spark-dependencies-elasticsearch $APP_HOME/jaeger-spark-dependencies-elasticsearch
COPY jaeger-spark-dependencies-common $APP_HOME/jaeger-spark-dependencies-common
COPY jaeger-spark-dependencies-test $APP_HOME/jaeger-spark-dependencies-test
COPY .mvn $APP_HOME/.mvn
COPY mvnw $APP_HOME

WORKDIR $APP_HOME

# Build module-specific shaded JAR based on VARIANT
# Cassandra variant: builds only cassandra module (no elasticsearch dependencies)
# Elasticsearch variants: build only elasticsearch module with specific connector version
# Unified variant: builds the mega-jar with both Cassandra and Elasticsearch support
RUN --mount=type=cache,target=/root/.m2 \
    if [ "$VARIANT" = "cassandra" ]; then \
      ./mvnw package --batch-mode -Dlicense.skip=true -DskipTests -pl jaeger-spark-dependencies-cassandra -am && \
      mkdir -p /tmp/jars && \
      cp $APP_HOME/jaeger-spark-dependencies-cassandra/target/jaeger-spark-dependencies-cassandra-0.0.1-SNAPSHOT.jar /tmp/jars/app.jar; \
    elif [ "$VARIANT" = "unified" ]; then \
      ./mvnw package --batch-mode -Dlicense.skip=true -DskipTests -Dversion.elasticsearch.spark=${ELASTICSEARCH_SPARK_VERSION} && \
      mkdir -p /tmp/jars && \
      cp $APP_HOME/jaeger-spark-dependencies/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar /tmp/jars/app.jar; \
    else \
      ./mvnw package --batch-mode -Dlicense.skip=true -DskipTests -Dversion.elasticsearch.spark=${ELASTICSEARCH_SPARK_VERSION} -pl jaeger-spark-dependencies-elasticsearch -am && \
      mkdir -p /tmp/jars && \
      cp $APP_HOME/jaeger-spark-dependencies-elasticsearch/target/jaeger-spark-dependencies-elasticsearch-0.0.1-SNAPSHOT.jar /tmp/jars/app.jar; \
    fi

FROM eclipse-temurin:21.0.9_10-jre@sha256:b0f6befb3f2af49704998c4425cb6313c1da505648a8e78cee731531996f735d
LABEL org.opencontainers.image.authors="The Jaeger Authors <cncf-jaeger-maintainers@lists.cncf.io>"

# Carry forward the VARIANT build arg to the runtime stage
ARG VARIANT=elasticsearch9

ENV APP_HOME=/app/
ENV VARIANT_TYPE=${VARIANT}

# Copy the built JAR
COPY --from=builder /tmp/jars/app.jar $APP_HOME/app.jar

WORKDIR $APP_HOME

COPY entrypoint.sh /

RUN chgrp root /etc/passwd && chmod g+rw /etc/passwd
USER 185

ENTRYPOINT ["/entrypoint.sh"]
