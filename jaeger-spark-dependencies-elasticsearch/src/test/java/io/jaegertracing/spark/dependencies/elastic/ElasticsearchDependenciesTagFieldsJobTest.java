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
    jaegerElasticsearchEnvironment.start(jaegerEnvSetting, jaegerVersion());
    collectorUrl = jaegerElasticsearchEnvironment.getCollectorUrl();
    zipkinCollectorUrl = jaegerElasticsearchEnvironment.getZipkinCollectorUrl();
    queryUrl = jaegerElasticsearchEnvironment.getQueryUrl();
  }
}
