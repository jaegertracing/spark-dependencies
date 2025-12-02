/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.spark.dependencies.model;

import java.io.Serializable;
import java.util.List;

/**
 * @author Pavol Loffay
 */
public class Span implements Serializable {

  private static final long serialVersionUID = 0L;

  private String traceId;
  private Long spanId;

  private Long startTime;
  private Process process;
  private List<KeyValue> tags;
  private List<Reference> refs;

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

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(Long startTime) {
    this.startTime = startTime;
  }

  public Process getProcess() {
    return process;
  }

  public void setProcess(Process process) {
    this.process = process;
  }

  public List<KeyValue> getTags() {
    return tags;
  }

  public String getTag(String key){
    for (KeyValue kv : tags){
      if (kv.getKey().equals(key)){
        return kv.getValueString();
      }
    }
    return null;
  }

  public void setTags(List<KeyValue> tags) {
    this.tags = tags;
  }

  public List<Reference> getRefs() {
    return refs;
  }

  public void setRefs(List<Reference> refs) {
    this.refs = refs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Span span = (Span) o;

    if (traceId != null ? !traceId.equals(span.traceId) : span.traceId != null) {
      return false;
    }
    if (spanId != null ? !spanId.equals(span.spanId) : span.spanId != null) {
      return false;
    }
    if (startTime != null ? !startTime.equals(span.startTime) : span.startTime != null) {
      return false;
    }
    if (process != null ? !process.equals(span.process) : span.process != null) {
      return false;
    }
    if (tags != null ? !tags.equals(span.tags) : span.tags != null) {
      return false;
    }
    return refs != null ? refs.equals(span.refs) : span.refs == null;
  }

  @Override
  public int hashCode() {
    int result = traceId != null ? traceId.hashCode() : 0;
    result = 31 * result + (spanId != null ? spanId.hashCode() : 0);
    result = 31 * result + (startTime != null ? startTime.hashCode() : 0);
    result = 31 * result + (process != null ? process.hashCode() : 0);
    result = 31 * result + (tags != null ? tags.hashCode() : 0);
    result = 31 * result + (refs != null ? refs.hashCode() : 0);
    return result;
  }
}
