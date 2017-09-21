package io.jaegertracing.spark.dependencies.test.tree;

import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class TreeGeneratorTest {

  @Test
  public void testJaeger() {
    Node<JaegerWrapper> root = new TreeGenerator(
        TracersGenerator.generateJaeger(20, "http://localhost:9411/api/traces"))
        .generateTree(10, 3);

    new Traversals().inorder(root, (node, parent) -> {
      System.out.println((parent == null ? "null" : parent.getTracingWrapper().operationName() + " & "
          + node.getTracingWrapper().operationName()));
    });
    Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
  }

  @Test
  public void testZipkin() {
    Node<ZipkinWrapper> root = new TreeGenerator(
        TracersGenerator.generateZipkin(20, "http://localhost:9411/api/traces"))
        .generateTree(10, 3);

    new Traversals().inorder(root, (node, parent) -> {
      System.out.println((parent == null ? "null" : parent.getTracingWrapper().operationName() + " & "
          + node.getTracingWrapper().operationName()));
    });
    Traversals.inorder(root, (node, parent) -> node.getTracingWrapper().get().getSpan().finish());
  }
}
