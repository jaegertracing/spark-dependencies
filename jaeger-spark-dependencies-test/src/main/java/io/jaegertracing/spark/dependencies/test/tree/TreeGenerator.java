/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
