/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.test.tree;

import java.util.function.BiConsumer;

/**
 * @author Pavol Loffay
 */
public class Traversals {

  /**
   * Traverse tree postOrder
   *
   * @param root root node
   * @param fce <node, parent>
   */
  public static <T extends TracingWrapper> void postOrder(Node<T> root, BiConsumer<Node<T>, Node<T>> fce) {
    postOrder(null, root, fce);
  }

  /**
   * @param node node
   * @param fce <node, parent>
   */
  private static <T extends TracingWrapper> void postOrder(Node<T> parent, Node<T> node, BiConsumer<Node<T>, Node<T>> fce) {
    for (Node descendant : node.getDescendants()) {
      postOrder(node, descendant, fce);
    }
    fce.accept(node, parent);
  }
}
