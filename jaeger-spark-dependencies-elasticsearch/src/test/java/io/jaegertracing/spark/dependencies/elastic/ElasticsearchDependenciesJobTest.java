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


import io.jaegertracing.Tracer;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJobTest extends DependenciesTest {

  private ElasticsearchDependenciesJob dependenciesJob;
  static JaegerElasticsearchEnvironment jaegerElasticsearchEnvironment;

  @BeforeClass
  public static void beforeClass() {
    jaegerElasticsearchEnvironment = new JaegerElasticsearchEnvironment();
    jaegerElasticsearchEnvironment.start(new HashMap<>(), jaegerVersion());
    collectorUrl = jaegerElasticsearchEnvironment.getCollectorUrl();
    zipkinCollectorUrl = jaegerElasticsearchEnvironment.getZipkinCollectorUrl();
    queryUrl = jaegerElasticsearchEnvironment.getQueryUrl();
  }

  @Before
  public void before() {
    Tracer initStorageTracer = TracersGenerator.createJaeger(UUID.randomUUID().toString(), collectorUrl).getA();
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitJaegerQueryContains(initStorageTracer.getServiceName(), "foo");
  }

  @After
  public void after() throws IOException {
    jaegerElasticsearchEnvironment.cleanUp(dependenciesJob.indexDate("jaeger-span"), dependenciesJob.indexDate("jaeger-dependencies"));
  }

  @AfterClass
  public static void afterClass() {
    jaegerElasticsearchEnvironment.stop();
  }

  @Override
  protected void deriveDependencies() {
    dependenciesJob = ElasticsearchDependenciesJob.builder()
        .nodes("http://" + jaegerElasticsearchEnvironment.getElasticsearchIPPort())
        .day(LocalDate.now())
        .build();
    dependenciesJob.run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // TODO otherwise elastic drops some spans
    TimeUnit.SECONDS.sleep(2);
  }

  public static class BoundPortHttpWaitStrategy extends HttpWaitStrategy {
    private final int port;

    public BoundPortHttpWaitStrategy(int port) {
      this.port = port;
    }

    @Override
    protected Set<Integer> getLivenessCheckPorts() {
      int mapptedPort = this.waitStrategyTarget.getMappedPort(port);
      return Collections.singleton(mapptedPort);
    }
  }
}
