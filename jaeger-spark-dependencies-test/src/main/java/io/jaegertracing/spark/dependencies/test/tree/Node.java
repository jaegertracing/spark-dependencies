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
