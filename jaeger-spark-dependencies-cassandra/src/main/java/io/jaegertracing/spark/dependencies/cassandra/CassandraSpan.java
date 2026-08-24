/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.cassandra;

import io.jaegertracing.spark.dependencies.model.Reference;
import io.jaegertracing.spark.dependencies.model.Span;
import java.util.ArrayList;
import java.util.Collections;
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
    List<Reference> existing = super.getRefs();
    List<Reference> references = existing == null
        ? new ArrayList<>()
        : new ArrayList<>(existing);
    if (parentId != null && parentId != 0L) {
      Reference legacyParent = new Reference();
      legacyParent.setSpanId(parentId);
      if (!references.contains(legacyParent)) {
        references.add(legacyParent);
      }
    }
    return Collections.unmodifiableList(references);
  }
}
