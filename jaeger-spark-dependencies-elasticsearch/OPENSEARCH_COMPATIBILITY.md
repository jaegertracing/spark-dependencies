# OpenSearch Compatibility

## Overview
The Jaeger Spark Dependencies job now supports both Elasticsearch and OpenSearch backends with automatic detection.

## Implementation

### Automatic Backend Detection
- The job queries the cluster root endpoint (`/`) to detect the backend type
- If `version.distribution` field contains "opensearch" (case-insensitive), it uses OpenSearch connectors
- Otherwise, it uses Elasticsearch connectors

### Dual Connector Support
- **Elasticsearch**: Uses `elasticsearch-hadoop` connector (`JavaEsSpark`)
- **OpenSearch**: Uses `opensearch-spark` connector (`JavaOpenSearchSpark`) 

### Configuration
- No configuration changes needed
- Same connection parameters work for both backends (e.g., `es.nodes`, `es.net.http.auth.user`)

## Testing
- **Unit Tests**: `ElasticsearchDependenciesJobUnitTest` validates JSON parsing for backend detection
- **Integration Tests**: `OpenSearchDependenciesJobTest` tests against containerized OpenSearch

## Usage
No changes required for existing deployments. The job automatically detects and adapts to the configured backend.
