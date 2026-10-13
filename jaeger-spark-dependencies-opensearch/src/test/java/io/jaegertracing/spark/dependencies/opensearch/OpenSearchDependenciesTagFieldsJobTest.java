/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import java.util.HashMap;
import org.junit.BeforeClass;

public class OpenSearchDependenciesTagFieldsJobTest extends OpenSearchDependenciesJobTest {

  @BeforeClass
  public static void beforeClass() {
    jaegerOpenSearchEnvironment = new JaegerOpenSearchEnvironment();
    HashMap<String, String> jaegerEnvSetting = new HashMap<>();
    jaegerEnvSetting.put("ES_TAGS__AS_FIELDS_ALL", "true");
    jaegerOpenSearchEnvironment.start(jaegerEnvSetting, jaegerVersion(),
        JaegerOpenSearchEnvironment.opensearchVersion());
    collectorUrl = jaegerOpenSearchEnvironment.getCollectorUrl();
    queryUrl = jaegerOpenSearchEnvironment.getQueryUrl();
  }
}
