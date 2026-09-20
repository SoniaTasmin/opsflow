/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real PostgreSQL every integration test runs against.
 *
 * <p>Deliberately not H2. H2 has no partial indexes, no exclusion constraints, different
 * {@code ON CONFLICT} semantics and no {@code jsonb} — all of which this schema depends on.
 * Testing against it would be testing a database we never run.
 *
 * <p>Spring caches application contexts by configuration, so declaring the container as a bean
 * here means one container is started once and shared by every test that imports this class,
 * rather than one per test class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    // Pinned to the same major version as production (see ADR-0007). A container
    // that drifts ahead of the deployed database tests the wrong thing.
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18-alpine");

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("opsflow")
                .withUsername("opsflow")
                .withPassword("test");
    }
}
