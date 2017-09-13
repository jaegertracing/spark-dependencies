package io.jaegertracing.spark.dependencies.cassandra.model;

import java.io.Serializable;
import java.util.List;

/**
 * @author Pavol Loffay
 */
public class Dependencies implements Serializable {
    private static final long serialVersionUID = 0L;

    private List<Dependency> dependencies;
    private long ts;

    public Dependencies(List<Dependency> dependencies, long ts) {
        this.dependencies = dependencies;
        this.ts = ts;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public long getTsIndex() {
        return ts;
    }

    public long getTs() {
        return ts;
    }
}
