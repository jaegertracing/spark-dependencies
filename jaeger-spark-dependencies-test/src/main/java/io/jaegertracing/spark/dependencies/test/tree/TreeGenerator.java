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

import io.jaegertracing.spark.dependencies.test.TracersGenerator.TracerHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * @author Pavol Loffay
 */
public class TreeGenerator<Tracer> {

  private Random tracersRandom = new Random();
  private List<TracerHolder<Tracer>> tracers;

  public TreeGenerator(List<TracerHolder<Tracer>> tracers) {
    if (tracers == null || tracers.isEmpty()) {
      throw new IllegalArgumentException();
    }
    this.tracers = new ArrayList<>(tracers);
  }

  public Node generateTree(int numOfNodes, int maxNumberOfDescendants) {
    if (numOfNodes <= 0 || maxNumberOfDescendants == 0) {
      throw new IllegalArgumentException();
    }

    Node root = new Node(tracers.get(0).tracingWrapper(), null);
    generateDescendants(new LinkedList<>(Collections.singletonList(root)), numOfNodes - 1, maxNumberOfDescendants);
    return root;
  }

  private void generateDescendants(Queue<Node> queue, int numOfNodes, final int maxNumberOfDescendants) {
    if (numOfNodes <= 0) {
      return;
    }

    Node parent = queue.poll();
    if (parent == null) {
      return;
    }
    for (int i = 0; i < maxNumberOfDescendants; i++) {
      Node descendant = new Node(tracers.get(tracersRandom.nextInt(tracers.size())).tracingWrapper(), parent);
      queue.add(descendant);
      if (--numOfNodes <= 0) {
        return;
      }
    }
    generateDescendants(queue, numOfNodes, maxNumberOfDescendants);
  }

  public List<TracerHolder<Tracer>> getTracers() {
    return Collections.unmodifiableList(tracers);
  }
}
