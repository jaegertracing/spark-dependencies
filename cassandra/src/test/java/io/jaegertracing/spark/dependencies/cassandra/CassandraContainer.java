package io.jaegertracing.spark.dependencies.cassandra;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.traits.LinkableContainer;
import org.testcontainers.shaded.com.google.common.net.HostAndPort;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;

/**
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

    private Cluster getCluster() {
        HostAndPort hap = HostAndPort.fromParts(getContainerIpAddress(), getMappedPort(9042));
        InetSocketAddress address = new InetSocketAddress(hap.getHostText(), hap.getPort());

        return Cluster.builder()
                .addContactPointsWithPorts(address)
                .withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(HostDistance.LOCAL, 1))
                .build();
    }
}
