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
