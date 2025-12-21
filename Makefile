.PHONY: e2e-cassandra e2e-es7 e2e-es8 e2e-es9 help

help:
	@echo "Available targets:"
	@echo "  e2e-cassandra  - Run Cassandra integration tests"
	@echo "  e2e-es7        - Run Elasticsearch 7 integration tests"
	@echo "  e2e-es8        - Run Elasticsearch 8 integration tests"
	@echo "  e2e-es9        - Run Elasticsearch 9 integration tests"

e2e-cassandra:
	@echo "Building Docker image for Cassandra variant..."
	docker build \
	  --build-arg VARIANT=cassandra \
	  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-cassandra \
	  .
	@echo "Running Cassandra integration tests..."
	SPARK_DEPENDENCIES_JOB_TAG=test-cassandra \
	./mvnw --batch-mode clean test -am -pl jaeger-spark-dependencies-cassandra

e2e-es7:
	@echo "Building Docker image for ES7 variant..."
	docker build \
	  --build-arg VARIANT=elasticsearch7 \
	  --build-arg ELASTICSEARCH_SPARK_VERSION=7.17.10 \
	  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es7 \
	  .
	@echo "Running ES7 integration tests..."
	SPARK_DEPENDENCIES_JOB_TAG=test-es7 \
	ELASTICSEARCH_VERSION=7.3.0 \
	./mvnw --batch-mode clean test -am \
	  -pl jaeger-spark-dependencies-elasticsearch \
	  -Dversion.elasticsearch.spark=7.17.10

e2e-es8:
	@echo "Building Docker image for ES8 variant..."
	docker build \
	  --build-arg VARIANT=elasticsearch8 \
	  --build-arg ELASTICSEARCH_SPARK_VERSION=8.13.4 \
	  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es8 \
	  .
	@echo "Running ES8 integration tests..."
	SPARK_DEPENDENCIES_JOB_TAG=test-es8 \
	ELASTICSEARCH_VERSION=8.3.1 \
	./mvnw --batch-mode clean test -am \
	  -pl jaeger-spark-dependencies-elasticsearch \
	  -Dversion.elasticsearch.spark=8.13.4

e2e-es9:
	@echo "Building Docker image for ES9 variant (unified/mega-jar)..."
	docker build \
	  --build-arg VARIANT=unified \
	  --build-arg ELASTICSEARCH_SPARK_VERSION=9.1.3 \
	  -t ghcr.io/jaegertracing/spark-dependencies/spark-dependencies:test-es9 \
	  .
	@echo "Running ES9 integration tests..."
	SPARK_DEPENDENCIES_JOB_TAG=test-es9 \
	ELASTICSEARCH_VERSION=9.1.3 \
	./mvnw --batch-mode clean test -am \
	  -pl jaeger-spark-dependencies-elasticsearch \
	  -Dversion.elasticsearch.spark=9.1.3
