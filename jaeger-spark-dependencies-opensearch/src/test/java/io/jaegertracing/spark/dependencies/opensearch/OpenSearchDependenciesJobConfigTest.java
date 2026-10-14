/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.LocalDate;
import org.apache.spark.SparkConf;
import org.junit.Test;

public class OpenSearchDependenciesJobConfigTest {

  @Test
  public void testDefaultReadFieldIncludeProperties() {
    OpenSearchDependenciesJob job = OpenSearchDependenciesJob.builder()
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertEquals("traceID,spanID,parentSpanID,startTime,process,process.*,tags,tags.*,tag,tag.*,references,references.*",
        conf.get("opensearch.read.field.include"));
    assertEquals("traceID,spanID,parentSpanID,startTime,process,process.*,tags,tags.*,tag,tag.*,references,references.*",
        job.getReadFieldInclude());
  }

  @Test
  public void testCustomReadFieldInclude() {
    OpenSearchDependenciesJob job = OpenSearchDependenciesJob.builder()
        .readFieldInclude("traceID,spanID,process")
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertEquals("traceID,spanID,process", conf.get("opensearch.read.field.include"));
    assertEquals("traceID,spanID,process", job.getReadFieldInclude());
  }

  @Test
  public void testDisabledReadFieldInclude() {
    OpenSearchDependenciesJob job = OpenSearchDependenciesJob.builder()
        .readFieldInclude(null)
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertFalse(conf.contains("opensearch.read.field.include"));
  }
}
