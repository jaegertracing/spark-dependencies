/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;

/**
 * @author Pavol Loffay
 */
public class Reference implements Serializable {
  private static final long serialVersionUID = 0L;

  private Long spanId;

  public Long getSpanId() {
    return spanId;
  }

  public void setSpanId(Long spanId) {
    this.spanId = spanId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Reference reference = (Reference) o;

    return spanId != null ? spanId.equals(reference.spanId) : reference.spanId == null;
  }

  @Override
  public int hashCode() {
    return spanId != null ? spanId.hashCode() : 0;
  }
}
