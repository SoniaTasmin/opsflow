/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A module may not issue SQL against tables another module owns. ArchUnit cannot see inside SQL
 * strings, so this reads the sources: without it the published-API rules pass while a module
 * reaches straight into another's schema.
 */
class CrossModuleSqlRulesTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/opsflow");

    private static final Map<String, List<String>> TABLES_BY_MODULE = Map.of(
            "identity", List.of("app_user"),
            "organization", List.of("organization", "org_membership"),
            "reporting",
                    List.of(
                            "metric_definition",
                            "reporting_period",
                            "reporting_period_metric",
                            "reporting_period_organization"),
            "submission", List.of("submission", "submission_value", "submission_transition"));

    @Test
    void noModuleQueriesAnotherModulesTables() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String module : TABLES_BY_MODULE.keySet()) {
            Path modulePath = SOURCE_ROOT.resolve(module);
            if (!Files.isDirectory(modulePath)) {
                continue;
            }
            List<String> ownTables = TABLES_BY_MODULE.get(module);

            try (Stream<Path> sources = Files.walk(modulePath)) {
                for (Path source : sources.filter(path -> path.toString().endsWith(".java"))
                        .toList()) {
                    String content = Files.readString(source);

                    TABLES_BY_MODULE.forEach((owner, tables) -> {
                        if (owner.equals(module)) {
                            return;
                        }
                        tables.stream()
                                .filter(table -> !ownTables.contains(table))
                                .filter(table -> referencesTable(content, table))
                                .forEach(table -> violations.add(module + " references " + owner
                                        + "-owned table opsflow." + table + " in " + SOURCE_ROOT.relativize(source)));
                    });
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private static boolean referencesTable(String content, String table) {
        // The lookbehind separates a schema-qualified table in SQL from the Java package
        // com.opsflow.<module>, which is a legitimate published-API import.
        return Pattern.compile("(?<!com\\.)opsflow\\." + Pattern.quote(table) + "\\b")
                .matcher(content)
                .find();
    }
}
