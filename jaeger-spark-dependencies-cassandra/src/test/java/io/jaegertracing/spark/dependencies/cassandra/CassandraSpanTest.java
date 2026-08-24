/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.cassandra;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.spark.dependencies.model.Reference;
import java.util.Collections;
import org.junit.Test;

public class CassandraSpanTest {

  @Test
  public void shouldNotDuplicateParentWhenParentIdAlreadyInRefs() {
    Reference childOf = new Reference();
    childOf.setSpanId(1L);

    CassandraSpan span = new CassandraSpan();
    span.setParentId(1L);
    span.setRefs(Collections.singletonList(childOf));

    assertEquals(1, span.getRefs().size());
    assertEquals(1L, span.getRefs().get(0).getSpanId().longValue());
  }

  @Test
  public void shouldKeepLegacyParentIdWhenRefsEmpty() {
    CassandraSpan span = new CassandraSpan();
    span.setParentId(1L);

    assertEquals(1, span.getRefs().size());
    assertEquals(1L, span.getRefs().get(0).getSpanId().longValue());
  }

  @Test
  public void shouldIgnoreNullAndZeroParentId() {
    CassandraSpan span = new CassandraSpan();
    span.setParentId(0L);

    assertEquals(0, span.getRefs().size());
  }
}
