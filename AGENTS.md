# Integration Tests Guide

This guide provides instructions for running integration tests for the Jaeger Spark Dependencies project.

For detailed information about integration tests, including prerequisites, troubleshooting, and environment variables, see the [Running Integration Tests](README.md#running-integration-tests) section in the README.

## Quick Start

The project includes make targets for running integration tests against different storage backends:

```bash
make e2e-cassandra  # Run Cassandra 4.x integration tests
make e2e-es7        # Run Elasticsearch 7 integration tests
make e2e-es8        # Run Elasticsearch 8 integration tests
make e2e-es9        # Run Elasticsearch 9 integration tests
```

Each target builds the appropriate Docker image and runs the corresponding integration test suite.

For more details, see the [Running Integration Tests](README.md#running-integration-tests) section in the README.
