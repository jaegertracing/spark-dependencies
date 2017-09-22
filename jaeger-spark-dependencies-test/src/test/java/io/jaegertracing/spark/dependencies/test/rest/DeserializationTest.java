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
package io.jaegertracing.spark.dependencies.test.rest;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class DeserializationTest {
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  @Test
  public void testDependencyLinkDeserialization() throws IOException {
    String json = "{\"data\":[{\"parent\":\"service1\",\"child\":\"service2\",\"callCount\":1}],\"total\":0," +
        "\"limit\":0,\"offset\":0,\"errors\":null}";

    RestResult<DependencyLink> restResult = objectMapper.readValue(json,  new TypeReference<RestResult<DependencyLink>>() {});
    Assert.assertEquals(null, restResult.getErrors());
    Assert.assertEquals(1, restResult.getData().size());
    Assert.assertEquals("service1", restResult.getData().get(0).getParent());
    Assert.assertEquals("service2", restResult.getData().get(0).getChild());
    Assert.assertEquals(1L, restResult.getData().get(0).getCallCount());
  }
}
