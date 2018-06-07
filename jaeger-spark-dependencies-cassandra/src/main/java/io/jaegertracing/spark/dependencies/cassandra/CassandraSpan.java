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
package io.jaegertracing.spark.dependencies.cassandra;

import io.jaegertracing.spark.dependencies.model.Reference;
import io.jaegertracing.spark.dependencies.model.Span;
import java.util.ArrayList;
import java.util.List;

/**
 * Jaeger > 1.5 does not store parentId. All references are stored in references table.
 * This class is used to maintain compatibility with older Jaeger deployments.
 *
 * @author Pavol Loffay
 */
public class CassandraSpan extends Span {

  private Long parentId;

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  @Override
  public List<Reference> getRefs() {
    ArrayList<Reference> references = new ArrayList<>(super.getRefs());
    Reference legacyParent = new Reference();
    legacyParent.setSpanId(parentId);
    references.add(legacyParent);
    return references;
  }
}
