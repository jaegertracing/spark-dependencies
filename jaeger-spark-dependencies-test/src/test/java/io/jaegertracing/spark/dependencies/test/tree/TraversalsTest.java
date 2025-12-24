/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.test.tree;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.spark.dependencies.test.MockTracingWrapper;
import io.opentracing.mock.MockTracer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class TraversalsTest {

  @Test
  public void testInorder() {
    Node<MockTracingWrapper> root = new Node<>(new MockTracingWrapper(new MockTracer(), "foo"), null);
    Node<MockTracingWrapper> child1 = new Node<>(new MockTracingWrapper(new MockTracer(), "child1"), root);
    Node<MockTracingWrapper> child2 = new Node<>(new MockTracingWrapper(new MockTracer(), "child2"), root);
    Node<MockTracingWrapper> child3 = new Node<>(new MockTracingWrapper(new MockTracer(), "child3"), root);

    List<Node> nodes = new ArrayList<>();
    Traversals.postOrder(root, (node, parent) -> {
      if (parent != null) {
        assertEquals(root, parent);
      } else {
        assertEquals(null, parent);
      }
      nodes.add(node);
    });
    assertEquals(new ArrayList<>(Arrays.asList(child1, child2, child3, root)), nodes);

    Node<MockTracingWrapper> child33 = new Node<>(new MockTracingWrapper(new MockTracer(), "child33"), child3);
    Node<MockTracingWrapper> child333 = new Node<>(new MockTracingWrapper(new MockTracer(), "child333"), child33);

    List<Node> nodes2 = new ArrayList<>();
    List<Node> parents2 = new ArrayList<>();
    Traversals.postOrder(root, (node, parent) -> {
      nodes2.add(node);
      parents2.add(parent);
    });
    assertEquals(new ArrayList<>(Arrays.asList(child1, child2, child333, child33, child3, root)), nodes2);
    assertEquals(new ArrayList<>(Arrays.asList(root, root, child33, child3, root, null)), parents2);
  }
}
