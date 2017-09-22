package io.jaegertracing.spark.dependencies.test.tree;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.ZipkinWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class TreeGeneratorTest {

  @Test
  public void testGenerateOne() {
    Node<JaegerWrapper> root = new TreeGenerator(TracersGenerator.generateJaeger(1, "http://localhost"))
        .generateTree(1, 3);
    assertEquals(0, root.getDescendants().size());
    assertNotNull(root.getServiceName());
    assertNotNull(root.getTracingWrapper().get().getSpan());
    assertNotNull(root.getTracingWrapper().get().operationName());
  }

  @Test
  public void testBranchingFactorOne() {
    Node<JaegerWrapper> root = new TreeGenerator(TracersGenerator.generateJaeger(1, "http://localhost"))
        .generateTree(16, 3);
    List<Node> nodes = new ArrayList<>();
    Traversals.inorder(root, (jaegerWrapperNode, jaegerWrapperNode2) -> {
      assertTrue(jaegerWrapperNode.getDescendants().size() <= 3);
      nodes.add(jaegerWrapperNode);
    });
    assertEquals(16, nodes.size());
  }

  @Test
  public void testGenerateOneZipkin() {
    Node<ZipkinWrapper> root = new TreeGenerator(TracersGenerator.generateZipkin(20, "http://localhost:9411/api/traces"))
        .generateTree(1, 3);
    assertEquals(0, root.getDescendants().size());
    assertNotNull(root.getServiceName());
    assertNotNull(root.getTracingWrapper().get().getSpan());
    assertNotNull(root.getTracingWrapper().get().operationName());
  }
}
