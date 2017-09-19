package io.jaegertracing.spark.dependencies.common.rest;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class DeserialiationTest {
    private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

    @Test
    public void testDeserialization() throws IOException {
        String json = "{\"data\":[{\"parent\":\"service1\",\"child\":\"service2\",\"callCount\":1}],\"total\":0," +
                "\"limit\":0,\"offset\":0,\"errors\":null}";

        RestResult<DependencyLink> restResult = objectMapper.readValue(json,  new TypeReference<RestResult<DependencyLink>>() {});
        assertEquals(null, restResult.getErrors());
        assertEquals(1, restResult.getData().size());
        assertEquals("service1", restResult.getData().get(0).getParent());
        assertEquals("service2", restResult.getData().get(0).getChild());
        assertEquals(1L, restResult.getData().get(0).getCallCount());
    }

    @Test
    public void test() {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .format(new Date()).toString());
    }
}
