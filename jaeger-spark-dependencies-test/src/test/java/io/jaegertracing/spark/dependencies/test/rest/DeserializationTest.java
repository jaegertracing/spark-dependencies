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
  public void testDeserialization() throws IOException {
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
