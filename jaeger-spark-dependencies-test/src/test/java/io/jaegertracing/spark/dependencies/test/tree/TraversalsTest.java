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
