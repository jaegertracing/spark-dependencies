package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * @author Pavol Loffay
 */
public class Span implements Serializable {
    private static final long serialVersionUID = 0L;

    private String traceId;
    private Long spanId;
    private Long parentId;

    private long startTime;
    private Process process;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
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

    // TODO for elasticsearch
    public void setSpanID(String hex) {
        BigInteger value = new BigInteger(hex, 16);
//        this.spanId = value.longValue();
    }
    public void setTraceID(String hex) {
//        this.traceIdArr = hex;
    }
    public void setParentSpanID(String hex) {
        BigInteger value = new BigInteger(hex, 16);
//        this.parentId = value.longValue();
    }
    public String getTraceIdArr() {
        return null;
    }
}

