/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rules that keep CSV import honest. Imports may group work and report failures; it may not
 * become a second writer of submission data, and it may not pretend to hold a request's identity
 * on a thread that has none.
 */
class ImportWritePathTest {

    private static final Path IMPORTS_SOURCE = Path.of("src/main/java/com/opsflow/imports");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void importsReachesSubmissionOnlyThroughItsPublishedContract() {
        noClasses()
                .that()
                .resideInAPackage("com.opsflow.imports..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.opsflow.submission.domain..",
                        "com.opsflow.submission.application..",
                        "com.opsflow.submission.infrastructure..",
                        "com.opsflow.submission.api..")
                .allowEmptyShould(true)
                .because("imports may use SubmissionImportService and nothing below it (ADR-0006)")
                .check(classes);
    }

    @Test
    void importsHoldsNoPersistenceOfItsOwnForSubmissionData() {
        noClasses()
                .that()
                .resideInAPackage("com.opsflow.imports..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .allowEmptyShould(true)
                .because("submission rows are written by the submission module alone")
                .check(classes);
    }

    /**
     * A batch thread has no authenticated request behind it. Reading the holder there returns an
     * empty context that an authorization check would quietly accept, which is why the authority
     * travels on the job row instead.
     */
    @Test
    void batchCodeNeverReadsTheSecurityContext() {
        noClasses()
                .that()
                .resideInAPackage("com.opsflow.imports..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
                .allowEmptyShould(true)
                .because("asynchronous work carries durable authority, never a request principal")
                .check(classes);
    }

    @Test
    void importsNeverUsesTheRequestScopedActorProvider() {
        noClasses()
                .that()
                .resideInAnyPackage("com.opsflow.imports.infrastructure..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.opsflow.identity.CurrentActorProvider")
                .allowEmptyShould(true)
                .because("only admission runs inside a request; processing must not ask for roles")
                .check(classes);
    }

    /** The whole point of streaming is undone by one call that materialises the file. */
    @Test
    void theImportPathNeverReadsAWholeFileIntoMemory() throws IOException {
        List<String> forbidden = List.of("Files.readAllBytes", "Files.readAllLines", "Files.readString", ".getBytes()");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(IMPORTS_SOURCE)) {
            for (Path source :
                    sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                forbidden.stream()
                        .filter(content::contains)
                        .forEach(call -> violations.add(IMPORTS_SOURCE.relativize(source) + " calls " + call));
            }
        }

        assertThat(violations).isEmpty();
    }

    /** No persisted import field may carry anything a token or a role could hide in. */
    @Test
    void theImportJobRecordsNoRolesOrCredentials() throws IOException {
        List<String> forbidden = List.of("roles", "token", "credential", "password", "authorities");
        String schema = Files.readString(Path.of("src/main/resources/db/migration/V6__imports.sql"))
                .toLowerCase(java.util.Locale.ROOT);

        List<String> columns = schema.lines()
                .map(String::strip)
                .filter(line -> forbidden.stream().anyMatch(line::startsWith))
                .toList();

        assertThat(columns).isEmpty();
    }
}
