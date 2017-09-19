package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author Pavol Loffay
 */
@JsonDeserialize(using = SpanDeserializer.class)
public class SpanMixin {

}
