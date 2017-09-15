package io.jaegertracing.spark.dependencies.rest;

/**
 * @author Pavol Loffay
 */
public class DependencyLink {
    private String parent;
    private String child;
    private long callCount;

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
