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

FROM eclipse-temurin:11 as builder

ENV APP_HOME /app/

COPY pom.xml $APP_HOME
COPY jaeger-spark-dependencies $APP_HOME/jaeger-spark-dependencies
COPY jaeger-spark-dependencies-cassandra $APP_HOME/jaeger-spark-dependencies-cassandra
COPY jaeger-spark-dependencies-elasticsearch $APP_HOME/jaeger-spark-dependencies-elasticsearch
COPY jaeger-spark-dependencies-common $APP_HOME/jaeger-spark-dependencies-common
COPY jaeger-spark-dependencies-test $APP_HOME/jaeger-spark-dependencies-test
COPY .mvn $APP_HOME/.mvn
COPY mvnw $APP_HOME

WORKDIR $APP_HOME
RUN --mount=type=cache,target=/root/.m2 ./mvnw package --batch-mode -Dlicense.skip=true -DskipTests

FROM eclipse-temurin:11-jre
MAINTAINER Pavol Loffay <ploffay@redhat.com>
ENV APP_HOME /app/
COPY --from=builder $APP_HOME/jaeger-spark-dependencies/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar $APP_HOME/

WORKDIR $APP_HOME

COPY entrypoint.sh /

RUN chgrp root /etc/passwd && chmod g+rw /etc/passwd
USER 185

ENTRYPOINT ["/entrypoint.sh"]
CMD java ${JAVA_OPTS} -jar $APP_HOME/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar
