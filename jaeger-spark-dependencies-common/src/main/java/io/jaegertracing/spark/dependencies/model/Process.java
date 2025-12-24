/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;

/**
 * @author Pavol Loffay
 */
public class Process implements Serializable {
  private static final long serialVersionUID = 0L;

  private String serviceName;

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Process)) {
      return false;
    }

    Process process = (Process) o;

    return serviceName != null ? serviceName.equals(process.serviceName)
        : process.serviceName == null;
  }

  @Override
  public int hashCode() {
    return serviceName != null ? serviceName.hashCode() : 0;
  }
}
