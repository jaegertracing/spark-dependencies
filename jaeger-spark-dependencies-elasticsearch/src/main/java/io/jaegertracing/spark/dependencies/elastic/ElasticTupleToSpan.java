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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaegertracing.spark.dependencies.elastic.json.JsonHelper;
import io.jaegertracing.spark.dependencies.model.Span;
import org.apache.spark.api.java.function.Function;
import scala.Tuple2;

/**
 * @author Pavol Loffay
 */
public class ElasticTupleToSpan implements Function<Tuple2<String, String>, Span> {

  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  @Override
  public Span call(Tuple2<String, String> tuple) throws Exception {
    return objectMapper.readValue(tuple._2(), Span.class);
  }
}
