/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.spark.dependencies.elastic;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.IOException;

/**
 * Unit tests for OpenSearch detection logic
 */
public class ElasticsearchDependenciesJobUnitTest {
  
  @Test
  public void testDetectOpenSearch_ValidResponse() throws IOException {
    String opensearchResponse = "{\"version\":{\"distribution\":\"opensearch\",\"number\":\"2.5.0\"}}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertTrue("Should detect OpenSearch from valid response", 
               job.isOpenSearchFromJson(opensearchResponse));
  }
  
  @Test
  public void testDetectElasticsearch_NoDistribution() throws IOException {
    String esResponse = "{\"version\":{\"number\":\"7.17.0\"}}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertFalse("Should detect Elasticsearch when distribution field missing", 
                job.isOpenSearchFromJson(esResponse));
  }
  
  @Test
  public void testDetectOpenSearch_CaseInsensitive() throws IOException {
    String response = "{\"version\":{\"distribution\":\"OpenSearch\",\"number\":\"1.3.0\"}}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertTrue("Should handle case-insensitive 'OpenSearch'", 
               job.isOpenSearchFromJson(response));
  }
  
  @Test
  public void testDetectOpenSearch_Lowercase() throws IOException {
    String response = "{\"version\":{\"distribution\":\"opensearch\",\"number\":\"2.0.0\"}}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertTrue("Should handle lowercase 'opensearch'", 
               job.isOpenSearchFromJson(response));
  }
  
  @Test
  public void testNullJson_ReturnsFalse() throws IOException {
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertFalse("Null JSON should return false", 
                job.isOpenSearchFromJson(null));
  }
  
  @Test
  public void testEmptyJson_ReturnsFalse() throws IOException {
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertFalse("Empty JSON should return false", 
                job.isOpenSearchFromJson(""));
  }
  
  @Test
  public void testMissingVersionField() throws IOException {
    String json = "{\"name\":\"my-cluster\",\"cluster_uuid\":\"abc123\"}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertFalse("Missing version field should return false", 
                job.isOpenSearchFromJson(json));
  }
  
  @Test
  public void testVersionNotMap() throws IOException {
    String json = "{\"version\":\"7.17.0\"}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertFalse("Version as string (not object) should return false", 
                job.isOpenSearchFromJson(json));
  }
  
  @Test
  public void testMalformedJson_ThrowsException() {
    String badJson = "{broken json";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertThrows("Malformed JSON should throw IOException", 
                 IOException.class, 
                 () -> job.isOpenSearchFromJson(badJson));
  }
  
  @Test
  public void testComplexOpenSearchResponse() throws IOException {
    String fullResponse = "{\n" +
        "  \"name\": \"opensearch-node-1\",\n" +
        "  \"cluster_name\": \"opensearch\",\n" +
        "  \"cluster_uuid\": \"xyz789\",\n" +
        "  \"version\": {\n" +
        "    \"distribution\": \"opensearch\",\n" +
        "    \"number\": \"2.5.0\",\n" +
        "    \"build_type\": \"tar\",\n" +
        "    \"build_hash\": \"abc123\"\n" +
        "  },\n" +
        "  \"tagline\": \"The OpenSearch Project\"\n" +
        "}";
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder().build();
    
    assertTrue("Should detect OpenSearch from full response", 
               job.isOpenSearchFromJson(fullResponse));
  }
}
