package io.jaegertracing.spark.dependencies.test.tree;

import io.jaegertracing.spark.dependencies.test.TracersGenerator.TracerServiceName;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * @author Pavol Loffay
 */
public class TreeGenerator<Tracer> {

  private Random descendantsRandom = new Random();
  private Random tracersRandom = new Random();
  private List<TracerServiceName<Tracer>> tracers;

  public TreeGenerator(List<TracerServiceName<Tracer>> tracers) {
    this.tracers = tracers;
  }

  public Node generateTree(int numOfNodes, int maxNumberOfDescendants) {
    if (numOfNodes == 0 || maxNumberOfDescendants == 0 || tracers == null || tracers.isEmpty()) {
      throw new IllegalArgumentException();
    }

    Node root = new Node(tracers.get(0).tracingWrapper(), null);
    generateDescendants(new LinkedList<>(Collections.singletonList(root)), numOfNodes - 1, maxNumberOfDescendants);
    return root;
  }

  private void generateDescendants(Queue<Node> queue, int numOfNodes, int maxNumberOfDescendants) {
    Node parent = queue.poll();
    if (parent == null) {
      return;
    }
    // +1 to assure that we generate all exact number of nodes
    int numOfDescendants = descendantsRandom.nextInt(maxNumberOfDescendants) + 1;
    for (int i = 0; i < numOfDescendants; i++) {
      Node descendant = new Node(tracers.get(tracersRandom.nextInt(tracers.size())).tracingWrapper(), parent);
      queue.add(descendant);
      parent.addDescendant(descendant);
      if (--numOfNodes <= 0) {
        return;
      }
    }
    generateDescendants(queue, numOfNodes, maxNumberOfDescendants);
  }

  public List<TracerServiceName<Tracer>> getTracers() {
    return Collections.unmodifiableList(tracers);
  }
}
