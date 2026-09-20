/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the migrate-then-validate mechanism works end to end.
 *
 * <p>Reaching this test at all means Flyway migrated an empty PostgreSQL and Hibernate then
 * validated its mappings against the migrated schema — the context would have failed to start
 * otherwise. That combination is what catches entity/migration drift at build time instead of
 * as a 500 in production, and it is the single most valuable test in the harness once entities
 * exist from M3 onwards.
 */
@IntegrationTest
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void baselineMigrationIsApplied() {
        List<String> applied = jdbc.queryForList(
                "SELECT script FROM opsflow.flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(applied).contains("V1__baseline.sql");
    }

    @Test
    void everyAppliedMigrationSucceeded() {
        Integer failures = jdbc.queryForObject(
                "SELECT count(*) FROM opsflow.flyway_schema_history WHERE success = false", Integer.class);

        assertThat(failures).isZero();
    }

    @Test
    void businessSchemaExists() {
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'opsflow'", Integer.class);

        assertThat(found).isEqualTo(1);
    }

    @Test
    void databaseMajorVersionMatchesProduction() {
        // Guards against the container image drifting away from the deployed major version:
        // testing on a different major than we run is how version-specific behaviour slips
        // through (PostgreSQL 18 changed the image's data directory layout, for instance).
        Integer major = jdbc.queryForObject("SELECT current_setting('server_version_num')::int / 10000", Integer.class);

        assertThat(major).isEqualTo(18);
    }
}
