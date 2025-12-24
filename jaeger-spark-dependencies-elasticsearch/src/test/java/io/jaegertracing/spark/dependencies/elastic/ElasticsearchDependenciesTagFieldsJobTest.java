/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import java.util.HashMap;
import org.junit.BeforeClass;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesTagFieldsJobTest extends ElasticsearchDependenciesJobTest {

  @BeforeClass
  public static void beforeClass() {
    jaegerElasticsearchEnvironment = new JaegerElasticsearchEnvironment();
    HashMap<String, String> jaegerEnvSetting = new HashMap<>();
    jaegerEnvSetting.put("ES_TAGS__AS_FIELDS_ALL", "true");
    jaegerElasticsearchEnvironment.start(jaegerEnvSetting, jaegerVersion(), JaegerElasticsearchEnvironment.elasticsearchVersion());
    collectorUrl = jaegerElasticsearchEnvironment.getCollectorUrl();
    queryUrl = jaegerElasticsearchEnvironment.getQueryUrl();
  }
}
