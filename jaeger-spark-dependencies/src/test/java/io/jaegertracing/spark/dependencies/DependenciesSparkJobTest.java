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
package io.jaegertracing.spark.dependencies;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

public class DependenciesSparkJobTest {

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
  public void parseDateMidnightUTC() throws ParseException {
    long date = DependenciesSparkJob.parseDay("2013-05-15");
    assertThat(date).isEqualTo(Utils.midnightUTC(date));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseDateMalformed() throws ParseException {
    DependenciesSparkJob.parseDay("2013/05/15");
  }
}
