package io.jaegertracing.spark.dependencies.tree;

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
    public static <T> void inorder(Node<T> node, BiConsumer<Node<T>, Node<T>> fce) {
        inorderRec(node, fce);
        fce.accept(node, null);
    }

    private static <T> void inorderRec(Node<T> node, BiConsumer<Node<T>, Node<T>> fce) {
        for (Node descendant: node.getDescendants()) {
            inorderRec(descendant, fce);
            fce.accept(descendant, node);
        }
    }
}
