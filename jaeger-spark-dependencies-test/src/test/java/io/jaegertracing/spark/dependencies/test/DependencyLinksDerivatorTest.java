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
package io.jaegertracing.spark.dependencies.test;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.spark.dependencies.test.rest.DependencyLink;
import io.jaegertracing.spark.dependencies.test.tree.Node;
import io.opentracing.mock.MockTracer;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/**
 * @author Pavol Loffay
 */
public class DependencyLinksDerivatorTest {

  @Test
  public void testRootToMap() {
    Node<MockTracingWrapper> root = new Node<>(new MockTracingWrapper(new MockTracer(), "foo"), null);
    new Node<>(new MockTracingWrapper(new MockTracer(), "child1"), root);
    new Node<>(new MockTracingWrapper(new MockTracer(), "child1"), root);
    new Node<>(new MockTracingWrapper(new MockTracer(), "child2"), root);
    Node<MockTracingWrapper> child3 = new Node<>(new MockTracingWrapper(new MockTracer(), "child3"), root);
    Node<MockTracingWrapper> child33 = new Node<>(new MockTracingWrapper(new MockTracer(), "child33"), child3);
    new Node<>(new MockTracingWrapper(new MockTracer(), "child333"), child33);

    Map<String, Map<String, Long>> depLinks = DependencyLinkDerivator.serviceDependencies(root);
    // 3 parents
    assertEquals(3, depLinks.size());
    assertEquals(3, depLinks.get("foo").size());
    assertEquals(1, depLinks.get("child3").size());
    assertEquals(1, depLinks.get("child33").size());

    assertEquals(Long.valueOf(2), depLinks.get("foo").get("child1"));
    assertEquals(Long.valueOf(1), depLinks.get("foo").get("child2"));
    assertEquals(Long.valueOf(1), depLinks.get("foo").get("child3"));
    assertEquals(Long.valueOf(1), depLinks.get("child3").get("child33"));
    assertEquals(Long.valueOf(1), depLinks.get("child33").get("child333"));
  }

  @Test
  public void testDepLinkToMap() {
    DependencyLink rootChild = new DependencyLink("root", "child", 3);
    DependencyLink childRoot = new DependencyLink("child", "root", 2);
    DependencyLink childChild2 = new DependencyLink("child", "child2", 6);

    Map<String, Map<String, Long>> depLinks = DependencyLinkDerivator.serviceDependencies(
        Arrays.asList(rootChild, childRoot, childChild2));

    assertEquals(2, depLinks.size());
    assertEquals(1, depLinks.get("root").size());
    assertEquals(2, depLinks.get("child").size());

    assertEquals(Long.valueOf(3), depLinks.get("root").get("child"));
    assertEquals(Long.valueOf(2), depLinks.get("child").get("root"));
    assertEquals(Long.valueOf(6), depLinks.get("child").get("child2"));
  }
}
