package io.jaegertracing.spark.dependencies.cassandra.model;

import java.io.Serializable;

/**
 * @author Pavol Loffay
 */
public class Dependency implements Serializable {
    private static final long serialVersionUID = 0L;

    private String parent;
    private String child;
    private int callCount;

    public Dependency(String parent, String child) {
        this(parent, child, 1);
    }

    public Dependency(String parent, String child, int callCount) {
        this.parent = parent;
        this.child = child;
        this.callCount = callCount;
    }

    public String getParent() {
        return parent;
    }

    public String getChild() {
        return child;
    }

    public int getCallCount() {
        return callCount;
    }
}
