FROM openjdk:alpine

MAINTAINER Pavol Loffay <ploffay@redhat.com>

ENV APP_HOME /app/

COPY pom.xml $APP_HOME
COPY cassandra $APP_HOME/cassandra
COPY main $APP_HOME/main
COPY .mvn $APP_HOME/.mvn
COPY mvnw $APP_HOME

WORKDIR $APP_HOME
RUN ./mvnw package -Dlicense.skip=true && rm -rf ~/.m2

CMD java -jar main/target/jaeger-spark-dependencies-0.0.1-SNAPSHOT.jar
