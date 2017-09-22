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
    Node<MockTracingWrapper> child1 = new Node<>(new MockTracingWrapper(new MockTracer(), "child1"), null);
    Node<MockTracingWrapper> child1Other = new Node<>(new MockTracingWrapper(new MockTracer(), "child1"), null);
    Node<MockTracingWrapper> child2 = new Node<>(new MockTracingWrapper(new MockTracer(), "child2"), null);
    Node<MockTracingWrapper> child3 = new Node<>(new MockTracingWrapper(new MockTracer(), "child3"), null);
    root.addDescendant(child1);
    root.addDescendant(child1Other);
    root.addDescendant(child2);
    root.addDescendant(child3);
    Node<MockTracingWrapper> child33 = new Node<>(new MockTracingWrapper(new MockTracer(), "child33"), null);
    Node<MockTracingWrapper> child333 = new Node<>(new MockTracingWrapper(new MockTracer(), "child333"), null);
    child3.addDescendant(child33);
    child33.addDescendant(child333);

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
