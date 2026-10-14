/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.LocalDate;
import org.apache.spark.SparkConf;
import org.junit.Test;

public class ElasticsearchDependenciesJobConfigTest {

  @Test
  public void testDefaultReadFieldIncludeProperties() {
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder()
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertEquals("traceID,spanID,parentSpanID,startTime,process,tags,tag,references",
        conf.get("es.read.source.filter"));
    assertEquals("traceID,spanID,parentSpanID,startTime,process,tags,tag,references",
        job.getReadFieldInclude());
  }

  @Test
  public void testCustomReadFieldInclude() {
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder()
        .readFieldInclude("traceID,spanID,process")
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertEquals("traceID,spanID,process", conf.get("es.read.source.filter"));
    assertEquals("traceID,spanID,process", job.getReadFieldInclude());
  }

  @Test
  public void testDisabledReadFieldInclude() {
    ElasticsearchDependenciesJob job = ElasticsearchDependenciesJob.builder()
        .readFieldInclude(null)
        .day(LocalDate.now())
        .build();

    SparkConf conf = job.getConf();
    assertFalse(conf.contains("es.read.source.filter"));
  }
}
