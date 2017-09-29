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
package io.opentracing.spark.dependencies.elastic;


import com.uber.jaeger.Tracer;
import io.jaegertracing.spark.dependencies.elastic.ElasticsearchDependenciesJob;
import io.jaegertracing.spark.dependencies.test.DependenciesTest;
import io.jaegertracing.spark.dependencies.test.TracersGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.Wait;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchDependenciesJobTest extends DependenciesTest {

  private Network network = Network.newNetwork();

  private GenericContainer elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:5.6.1")
          .withNetwork(network)
          .withNetworkAliases("elasticsearch")
          .waitingFor(Wait.forHttp("/"))
          .withExposedPorts(9200, 9300)
          .withEnv("xpack.security.enabled", "false")
          .withEnv("network.bind_host", "elasticsearch")
          .withEnv("network.host", "_site_")
          .withEnv("network.publish_host", "_local_");

  private GenericContainer jaegerCollector = new GenericContainer<>("jaegertracing/jaeger-collector:latest")
          .withNetwork(network)
          .withCommand("/go/bin/collector-linux",
                  "--es.server-urls=http://elasticsearch:9200",
                  "--span-storage.type=elasticsearch",
                  "--collector.zipkin.http-port=9411",
                  "--collector.queue-size=100000",
                  "--collector.num-workers=500")
          .waitingFor(Wait.forHttp("/").forStatusCode(204))
          // the first one is health check
          .withExposedPorts(14269, 14268, 14269, 9411);

  private GenericContainer jaegerQuery = new GenericContainer<>("jaegertracing/jaeger-query:latest")
          .withCommand("/go/bin/query-linux",
                  "--es.server-urls=http://elasticsearch:9200",
                  "--span-storage.type=elasticsearch")
          .withNetwork(network)
          .waitingFor(Wait.forHttp("/").forStatusCode(204))
          .withExposedPorts(16687, 16686);

  @Rule
  public RuleChain ruleChain = RuleChain.emptyRuleChain()
          .around(network)
          .around(elasticsearch)
          .around(jaegerCollector)
          .around(jaegerQuery);

  @Before
  public void before() {
    collectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(14268));
    zipkinCollectorUrl = String.format("http://%s:%d", jaegerCollector.getContainerIpAddress(), jaegerCollector.getMappedPort(9411));
    queryUrl = String.format("http://%s:%d", jaegerQuery.getContainerIpAddress(), jaegerQuery.getMappedPort(16686));

    Tracer initStorageTracer = TracersGenerator.createJaeger(UUID.randomUUID().toString(), collectorUrl).getA();
    initStorageTracer.buildSpan(UUID.randomUUID().toString()).withTag("foo", "bar").start().finish();
    initStorageTracer.close();
    waitJaegerQueryContains(initStorageTracer.getServiceName(), "foo");
  }

  @Override
  protected void deriveDependencies() {
    ElasticsearchDependenciesJob.builder()
        .nodes("http://" + elasticsearch.getContainerIpAddress() + ":" + elasticsearch.getMappedPort(9200))
        .day(LocalDate.now())
        .build()
        .run();
  }

  @Override
  protected void waitBetweenTraces() throws InterruptedException {
    // TODO otherwise elastic drops some spans
    TimeUnit.SECONDS.sleep(2);
  }
}
