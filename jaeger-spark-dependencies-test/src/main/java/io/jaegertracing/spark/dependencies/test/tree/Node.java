/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.test.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Node in tree with N descendants. Node encapsulates
 * {@link TracingWrapper} which holds tracing information e.g span/tracer.
 *
 * @author Pavol Loffay
 */
public class Node<T extends TracingWrapper> {
  private List<Node<T>> descendants = new ArrayList<>();

  private TracingWrapper<T> tracingWrapper;

  public Node(TracingWrapper<T> tracingWrapper, Node parent) {
    this.tracingWrapper = tracingWrapper;

    if (parent != null) {
      tracingWrapper.createChildSpan(parent.getTracingWrapper());
      parent.addDescendant(this);
    } else {
      tracingWrapper.createChildSpan(null);
    }
  }

  public TracingWrapper<T> getTracingWrapper() {
    return tracingWrapper;
  }

  private void addDescendant(Node descendant) {
    this.descendants.add(descendant);
  }

  public List<Node> getDescendants() {
    return Collections.unmodifiableList(descendants);
  }

  public String getServiceName() {
    return tracingWrapper.serviceName();
  }
}
