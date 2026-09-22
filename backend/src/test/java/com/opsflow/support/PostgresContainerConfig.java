/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Not H2: this schema depends on partial indexes, exclusion constraints and {@code ON CONFLICT}
 * semantics it does not have. Declared as a bean so Spring's context cache starts one container
 * for the whole suite rather than one per class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    // Same major version as production (ADR-0007): a container that drifts tests the wrong thing.
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
