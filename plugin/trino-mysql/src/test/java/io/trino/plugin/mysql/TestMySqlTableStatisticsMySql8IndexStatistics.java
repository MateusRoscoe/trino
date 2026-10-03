/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.mysql;

import io.trino.testing.MaterializedResult;
import org.junit.jupiter.api.Test;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.junit.jupiter.api.Assumptions.abort;

public class TestMySqlTableStatisticsMySql8IndexStatistics
        extends BaseMySqlTableStatisticsIndexStatisticsTest
{
    public TestMySqlTableStatisticsMySql8IndexStatistics()
    {
        super("mysql:8.0.30");
    }

    @Test
    @Override
    public void testNotAnalyzed()
    {
        abort("MySql8 automatically calculates stats - https://dev.mysql.com/doc/refman/8.0/en/innodb-parameters.html#sysvar_innodb_stats_auto_recalc");
    }

    @Test
    public void testFunctionalIndex()
    {
        String tableName = "test_stats_functional_index_" + randomNameSuffix();
        computeActual(format("CREATE TABLE %s AS SELECT orderkey, custkey FROM tpch.tiny.orders", tableName));
        try {
            executeInMysql(format("CREATE INDEX orderkey ON %s (orderkey)", tableName));
            // INFORMATION_SCHEMA.STATISTICS reports functional key parts with a NULL COLUMN_NAME
            executeInMysql(format("CREATE INDEX custkey_doubled ON %s ((custkey * 2))", tableName));
            executeInMysql(format("CREATE INDEX custkey_negated ON %s ((-custkey))", tableName));
            executeInMysql("ANALYZE TABLE " + tableName);

            MaterializedResult statsResult = computeActual("SHOW STATS FOR " + tableName);
            assertColumnStats(statsResult, new MapBuilder<String, Integer>()
                    .put("orderkey", 15000)
                    .put("custkey", null)
                    .build());
            assertThat(getTableCardinalityFromStats(statsResult)).isCloseTo(15000, withinPercentage(20));
        }
        finally {
            assertUpdate("DROP TABLE " + tableName);
        }
    }
}
