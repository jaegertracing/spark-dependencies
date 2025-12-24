/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic.json;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author Pavol Loffay
 */
@JsonDeserialize(using = SpanDeserializer.class)
public class SpanMixin {
}
