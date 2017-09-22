package io.jaegertracing.spark.dependencies.test.rest;

/**
 * @author Pavol Loffay
 */
public class DependencyLink {
  private String parent;
  private String child;
  private long callCount;

  // for jackson
  public DependencyLink() {}

  public DependencyLink(String parent, String child, long callCount) {
    this.parent = parent;
    this.child = child;
    this.callCount = callCount;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public String getChild() {
    return child;
  }

  public void setChild(String child) {
    this.child = child;
  }

  public long getCallCount() {
    return callCount;
  }

  public void setCallCount(long callCount) {
    this.callCount = callCount;
  }
}
