/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is a dependency, not an ambient global.
 *
 * <p>Nothing in the application calls {@code Instant.now()} directly: every component that needs
 * the current time takes a {@link Clock}. That is what makes deadline, period-boundary and
 * expiry behaviour testable without sleeping or waiting for a real date to arrive.
 *
 * <p>UTC everywhere. Timestamps are stored and compared as instants; presentation time zones are
 * a display concern.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
