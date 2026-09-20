/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Rules governing the web boundary.
 *
 * <p>These become enforceable from M1, when the first controllers and DTOs appear, and they
 * encode two commitments that are easy to erode one convenient shortcut at a time: persistence
 * types never become the API contract, and time is never read from an ambient global.
 */
class WebBoundaryRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void persistenceEntitiesAreNeverExposedThroughTheApi() {
        // The rule the whole DTO layer exists to uphold: an @Entity in a controller
        // signature makes the database schema the public API contract, so a column
        // rename becomes a breaking change for every client.
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .allowEmptyShould(true)
                .because("DTOs are the contract at the HTTP boundary; entities are an"
                        + " implementation detail of persistence")
                .check(classes);
    }

    @Test
    void controllersLiveOnlyInApiPackages() {
        classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .resideInAPackage("..api..")
                .allowEmptyShould(true)
                .because("the web layer is a module's api package; a controller elsewhere means"
                        + " the boundary has been bypassed")
                .check(classes);
    }

    @Test
    void timeIsInjectedRatherThanReadFromAnAmbientGlobal() {
        // Instant.now() and LocalDate.now() cannot be controlled from a test, so any
        // deadline, expiry or period-boundary rule using them is only testable by
        // waiting. Every component takes a Clock instead.
        noClasses()
                .that()
                .resideInAPackage("com.opsflow..")
                .and()
                .resideOutsideOfPackage("..platform.config..")
                .should()
                .callMethod(java.time.Instant.class, "now")
                .orShould()
                .callMethod(java.time.LocalDate.class, "now")
                .orShould()
                .callMethod(java.time.LocalDateTime.class, "now")
                .allowEmptyShould(true)
                .because("time is a dependency: inject java.time.Clock so date-sensitive rules"
                        + " can be tested without waiting")
                .check(classes);
    }

    @Test
    void domainExceptionsAreTheOnlyDeliberateFailureType() {
        noClasses()
                .that()
                .resideInAnyPackage("..domain..", "..application..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.server.ResponseStatusException")
                .allowEmptyShould(true)
                .because("HTTP status is a web concern; business failures are DomainException"
                        + " subtypes carrying an ErrorCode (ADR-0010)")
                .check(classes);
    }
}
