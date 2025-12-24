/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;

/**
 * @author Pavol Loffay
 */
public class KeyValue implements Serializable {
  private static final long serialVersionUID = 0L;

  private String key;
  private String valueType;

  // TODO there are more types: double, long, binary, not needed at the moment
  private String valueString;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValueString() {
    return valueString;
  }

  public void setValueString(String valueString) {
    this.valueString = valueString;
  }

  public String getValueType() {
    return valueType;
  }

  public void setValueType(String valueType) {
    this.valueType = valueType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    KeyValue keyValue = (KeyValue) o;

    if (key != null ? !key.equals(keyValue.key) : keyValue.key != null) {
      return false;
    }
    if (valueType != null ? !valueType.equals(keyValue.valueType) : keyValue.valueType != null) {
      return false;
    }
    return valueString != null ? valueString.equals(keyValue.valueString) : keyValue.valueString == null;
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + (valueType != null ? valueType.hashCode() : 0);
    result = 31 * result + (valueString != null ? valueString.hashCode() : 0);
    return result;
  }
}