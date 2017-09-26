/**
 * Copyright 2017 The Jaeger Authors
 * Copyright 2016-2017 The OpenZipkin Authors
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
package io.jaegertracing.spark.dependencies.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.traits.LinkableContainer;

/**
 * @author OpenZipkin authors
 * @author Pavol Loffay
 */
public class CassandraContainer extends GenericContainer<CassandraContainer> implements LinkableContainer {

  public CassandraContainer(String dockerImageName) {
    super(dockerImageName);
  }

  @Override
  protected void waitUntilContainerStarted() {
    Unreliables.retryUntilSuccess(120, TimeUnit.SECONDS, () -> {
      if (!isRunning()) {
        throw new ContainerLaunchException("Container failed to start");
      }

      try (Cluster cluster = getCluster(); Session session = cluster.newSession()) {
        session.execute("SELECT now() FROM system.local");
        logger().info("Obtained a connection to container ({})", cluster.getClusterName());
        return null; // unused value
      }
    });
  }

  public Cluster getCluster() {
    InetSocketAddress address = new InetSocketAddress(getContainerIpAddress(), getMappedPort(9042));

    return Cluster.builder()
        .addContactPointsWithPorts(address)
        .withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(HostDistance.LOCAL, 1))
        .build();
  }
}
