/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
