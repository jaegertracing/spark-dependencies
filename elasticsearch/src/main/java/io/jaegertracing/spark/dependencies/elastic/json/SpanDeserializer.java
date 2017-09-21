package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.jaegertracing.spark.dependencies.model.Process;
import io.jaegertracing.spark.dependencies.model.Span;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * @author Pavol Loffay
 */
public class SpanDeserializer extends StdDeserializer<Span> implements Serializable {

  // TODO Spark incorrectly serializes object mapper, therefore reinitializing here
  private ObjectMapper objectMapper = JsonHelper.configure(new ObjectMapper());

  public SpanDeserializer() {
    super(Span.class);
  }

  @Override
  public Span deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    JsonNode node = objectMapper.getFactory().setCodec(objectMapper).getCodec().readTree(jp);

    String spanIdHex = node.get("spanID").asText();
    String parentIdHex = node.get("parentSpanID").asText();
    String traceIdHex = node.get("traceID").asText();
    String startTimeStr = node.get("startTime").asText();

    BigInteger spanId = new BigInteger(spanIdHex, 16);
    BigInteger parentId = new BigInteger(parentIdHex, 16);

    JsonNode processNode = node.get("process");
    Process process = objectMapper.treeToValue(processNode, Process.class);

    Span span = new Span();
    span.setSpanId(spanId.longValue());
    span.setParentId(parentId.longValue());
    span.setTraceId(traceIdHex);
    span.setStartTime(startTimeStr != null ? Long.parseLong(startTimeStr) : null);
    span.setProcess(process);
    return span;
  }
}
