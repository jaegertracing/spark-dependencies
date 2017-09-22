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
