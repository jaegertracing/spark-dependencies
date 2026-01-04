/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.json;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author Pavol Loffay
 * @author Danish Siddiqui
 */
@JsonDeserialize(using = KeyValueDeserializer.class)
public class KeyValueMixin {
}
