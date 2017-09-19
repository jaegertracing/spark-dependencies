package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    public String getTimestamp() {
        // Jaeger ES dependency storage uses RFC3339Nano for timestamp
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .format(new Date(ts)).toString();
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }


//    public long getTsIndex() {
//        return ts;
//    }
//
//    public long getTs() {
//        return ts;
//    }
}
