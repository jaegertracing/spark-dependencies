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

import java.util.function.BiConsumer;

/**
 * @author Pavol Loffay
 */
public class Traversals {

  /**
   * Traverse tree inorder
   *
   * @param node root node
   * @param fce <node, parent>
   */
  public static <T extends TracingWrapper> void inorder(Node<T> node, BiConsumer<Node<T>, Node<T>> fce) {
    inorderRec(node, fce);
    fce.accept(node, null);
  }

  /**
   * @param node node
   * @param fce <node, parent>
   */
  private static <T extends TracingWrapper> void inorderRec(Node<T> node, BiConsumer<Node<T>, Node<T>> fce) {
    for (Node descendant: node.getDescendants()) {
      inorderRec(descendant, fce);
      fce.accept(descendant, node);
    }
  }
}
