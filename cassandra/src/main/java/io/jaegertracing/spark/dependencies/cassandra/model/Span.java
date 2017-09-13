package io.jaegertracing.spark.dependencies.cassandra.model;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * @author Pavol Loffay
 */
public class Span implements Serializable {
    private static final long serialVersionUID = 0L;

    private ByteBuffer traceId;
    private Long spanId;
    private Long parentId;

    private long startTime;
    private Process process;

    public ByteBuffer getTraceId() {
        return traceId;
    }

    public void setTraceId(ByteBuffer traceId) {
        this.traceId = traceId;
    }

    public Long getSpanId() {
        return spanId;
    }

    public void setSpanId(Long spanId) {
        this.spanId = spanId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public Process getProcess() {
        return process;
    }

    public void setProcess(Process process) {
        this.process = process;
    }
}

