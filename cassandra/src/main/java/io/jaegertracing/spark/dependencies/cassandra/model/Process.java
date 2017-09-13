package io.jaegertracing.spark.dependencies.cassandra.model;

import java.io.Serializable;

/**
 * @author Pavol Loffay
 */
public class Process implements Serializable {
    private static final long serialVersionUID = 0L;

    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
