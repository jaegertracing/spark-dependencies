package io.jaegertracing.spark.dependencies.tree;

import org.junit.Test;

import io.jaegertracing.spark.dependencies.TracersGenerator;

/**
 * @author Pavol Loffay
 */
public class TreeGeneratorTest {

    @Test
    public void test() {
        Node root = new TreeGenerator(TracersGenerator.generate(20, "http://localhost:9411/api/traces"))
                .generateTree(10, 3);

        new Traversals().inorder(root, (node, parent) -> {
            System.out.println((parent == null ? "null" : parent.getSpan().getOperationName()) + " & "
                    + node.getSpan().getOperationName());
        });
        Traversals.inorder(root, (node, parent) -> node.getSpan().finish());
        Traversals.inorder(root, (node, parent) -> node.getSpan().finish());
    }
}
