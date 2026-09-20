/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow;

import com.opsflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
class OpsFlowApplicationTest {

    @Test
    void contextLoads() {
        // Asserting nothing is the point: this fails if the Spring context cannot be built,
        // which now includes Flyway migrating a real database and Hibernate validating its
        // mappings against the resulting schema.
    }
}
