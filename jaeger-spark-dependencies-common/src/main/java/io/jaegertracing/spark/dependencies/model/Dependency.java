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

/**
 * @author Pavol Loffay
 */
public class Dependency implements Serializable {
  private static final long serialVersionUID = 0L;

  private final String parent;
  private final String child;
  private final long callCount;

  public Dependency(String parent, String child) {
    this(parent, child, 1);
  }

  public Dependency(String parent, String child, long callCount) {
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

  public long getCallCount() {
    return callCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Dependency)) {
      return false;
    }

    Dependency that = (Dependency) o;

    if (!parent.equals(that.parent)) {
      return false;
    }
    return (this.parent.equals(that.parent))
        && (this.child.equals(that.child))
        && this.callCount == that.callCount;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= parent.hashCode();
    h *= 1000003;
    h ^= child.hashCode();
    h *= 1000003;
    h ^= (int) (h ^ ((callCount >>> 32) ^ callCount));
    h *= 1000003;
    return h;
  }
}
