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
    Traversals.postOrder(root, (jaegerWrapperNode, jaegerWrapperNode2) -> {
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
