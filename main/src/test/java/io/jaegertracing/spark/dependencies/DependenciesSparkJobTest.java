package io.jaegertracing.spark.dependencies;

import static org.assertj.core.api.Assertions.assertThat;

import static io.jaegertracing.spark.dependencies.cassandra.CassandraDependenciesJob.midnightUTC;

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DependenciesSparkJobTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void parseDate() throws ParseException {
    // Date assertions don't assume UTC
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

    long date = DependenciesSparkJob.parseDay("2013-05-15");
    assertThat(new Date(date))
        .hasYear(2013)
        .hasMonth(5)
        .hasDayOfMonth(15);
  }

  @Test
  public void parseDate_midnightUTC() throws ParseException {
    long date = DependenciesSparkJob.parseDay("2013-05-15");
    assertThat(date)
        .isEqualTo(midnightUTC(date));
  }

  @Test
  public void parseDate_malformed() throws ParseException {
    thrown.expect(IllegalArgumentException.class);

    DependenciesSparkJob.parseDay("2013/05/15");
  }
}
