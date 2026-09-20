/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OpsFlowApplicationTest {

    @Test
    void contextLoads() {
        // Asserting nothing is the point: this fails if the Spring context
        // cannot be built at all, which is the cheapest possible smoke test.
    }
}
