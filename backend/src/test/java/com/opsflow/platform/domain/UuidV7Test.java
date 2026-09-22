/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void producesVersion7WithTheRfcVariant() {
        UUID id = UuidV7.generate(Clock.systemUTC());

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void encodesTheGeneratingTimestamp() {
        Instant when = Instant.parse("2026-09-20T12:00:00Z");

        UUID id = UuidV7.generate(Clock.fixed(when, ZoneOffset.UTC));

        assertThat(UuidV7.timestampOf(id)).isEqualTo(when);
    }

    @Test
    void identifiersGeneratedOverTimeSortInGenerationOrder() {
        // The reason for v7 over v4: inserts stay at the right edge of the index.
        List<UUID> ids = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        IntStream.range(0, 50).forEach(i -> ids.add(UuidV7.generate(start.plusMillis(i))));

        List<String> asGenerated = ids.stream().map(UUID::toString).toList();
        assertThat(asGenerated).isSorted();
    }

    @Test
    void identifiersAreUniqueWithinTheSameMillisecond() {
        Clock frozen = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

        var generated = IntStream.range(0, 2000)
                .mapToObj(i -> UuidV7.generate(frozen))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(generated).hasSize(2000);
    }
}
