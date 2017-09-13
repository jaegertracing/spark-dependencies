package io.jaegertracing.spark.dependencies.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import static io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob.parseHosts;
import static io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob.parsePort;

import org.junit.Test;

public class CassandraDependenciesJobTest {
  @Test
  public void parseHosts_ignoresPortSection() {
    assertThat(parseHosts("1.1.1.1:9142"))
        .isEqualTo("1.1.1.1");
  }

  @Test
  public void parseHosts_commaDelimits() {
    assertThat(parseHosts("1.1.1.1:9143,2.2.2.2:9143"))
        .isEqualTo("1.1.1.1,2.2.2.2");
  }

  @Test
  public void parsePort_ignoresHostSection() {
    assertThat(parsePort("1.1.1.1:9142"))
        .isEqualTo("9142");
  }

  @Test
  public void parsePort_multiple_consistent() {
    assertThat(parsePort("1.1.1.1:9143,2.2.2.2:9143"))
        .isEqualTo("9143");
  }

  @Test
  public void parsePort_defaultsTo9042() {
    assertThat(parsePort("1.1.1.1"))
        .isEqualTo("9042");
  }

  @Test
  public void parsePort_defaultsTo9042_multi() {
  }
}
