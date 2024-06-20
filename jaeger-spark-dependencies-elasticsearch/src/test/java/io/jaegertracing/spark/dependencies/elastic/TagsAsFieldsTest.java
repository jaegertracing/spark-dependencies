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

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.DependenciesSparkHelper;
import io.jaegertracing.spark.dependencies.elastic.json.JsonHelper;
import io.jaegertracing.spark.dependencies.model.Dependency;
import io.jaegertracing.spark.dependencies.model.Span;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.Test;

public class TagsAsFieldsTest {
    @Test
    public void shouldMapAllTags() throws IOException, ReflectiveOperationException {
        ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());
        updateEnv("ES_TAGS_AS_FIELDS_ALL", "true");

        List<Span> spans = objectMapper.readValue(this.getClass().getClassLoader().getResource("spans.json"), new TypeReference<List<Span>>(){});

        JavaSparkContext context = new JavaSparkContext("local[*]", "test");
        JavaPairRDD<String, Iterable<Span>> traces = context.parallelize(spans)
                .groupBy(Span::getTraceId);
        List<Dependency> dependencyLinks = DependenciesSparkHelper.derive(traces, "placeholder");

        context.stop();

        assertEquals(10, dependencyLinks.size());
    }

    @SuppressWarnings("unchecked")
    private static void updateEnv(String name, String val) throws ReflectiveOperationException {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        ((Map<String, String>) field.get(env)).put(name, val);
    }
}
