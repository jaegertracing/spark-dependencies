/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.test.tree;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import io.jaegertracing.spark.dependencies.test.tree.TracingWrapper.OpenTelemetryWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class TreeGeneratorTest {

  @Test
  public void testGenerateOne() {
    Node<OpenTelemetryWrapper> root = new TreeGenerator(TracersGenerator.generateJaeger(1, "http://localhost"))
        .generateTree(1, 3);
    assertEquals(0, root.getDescendants().size());
    assertNotNull(root.getServiceName());
    assertNotNull(root.getTracingWrapper().get().getSpan());
    assertNotNull(root.getTracingWrapper().get().operationName());
  }

  @Test
  public void testBranchingFactorOne() {
    Node<OpenTelemetryWrapper> root = new TreeGenerator(TracersGenerator.generateJaeger(1, "http://localhost"))
        .generateTree(16, 3);
    List<Node> nodes = new ArrayList<>();
    Traversals.postOrder(root, (jaegerWrapperNode, jaegerWrapperNode2) -> {
      assertTrue(jaegerWrapperNode.getDescendants().size() <= 3);
      nodes.add(jaegerWrapperNode);
    });
    assertEquals(16, nodes.size());
  }
}
