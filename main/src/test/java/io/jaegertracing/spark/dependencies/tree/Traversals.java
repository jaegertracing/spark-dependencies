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
    public static void inorder(Node node, BiConsumer<Node, Node> fce) {
        inorderRec(node, fce);
        fce.accept(node, null);
    }

    private static void inorderRec(Node node, BiConsumer<Node, Node> fce) {
        for (Node descendant: node.getDescendants()) {
            inorderRec(descendant, fce);
            fce.accept(descendant, node);
        }
    }
}
