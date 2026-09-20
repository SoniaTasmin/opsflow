/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Structural rules that hold across every module.
 *
 * <p>Each rule replaces a convention someone would otherwise have to remember
 * in code review. They are written as plain Jupiter tests invoking
 * {@code ArchRule.check(...)} rather than using the {@code @AnalyzeClasses}
 * extension, so nothing depends on ArchUnit's JUnit Platform integration —
 * which has not been verified against JUnit Platform 6.
 *
 * <p>{@code allowEmptyShould(true)} is set deliberately: at M0 most of these
 * packages do not exist yet, and ArchUnit fails a rule whose {@code that(...)}
 * clause matches nothing. The rules are installed before the code they govern
 * so that the first violation is caught by the build rather than by review.
 */
class CodingRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void domainLayersDependOnNoFramework() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "tools.jackson..")
                .allowEmptyShould(true)
                .because("the domain layer must be testable without Spring and must not be shaped"
                        + " by persistence or serialisation concerns")
                .check(classes);
    }

    @Test
    void transactionsAreDeclaredOnlyInApplicationServices() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..application..")
                .should()
                .beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .allowEmptyShould(true)
                .because("the transaction boundary is the use case: never a controller"
                        + " (which would span serialisation) and never a repository")
                .check(classes);
    }

    @Test
    void noFieldInjection() {
        noFields()
                .should()
                .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .allowEmptyShould(true)
                .because("field injection hides dependencies and makes a class untestable"
                        + " without a container; use constructor injection")
                .check(classes);
    }

    @Test
    void nothingWritesToStandardStreams() {
        noClasses()
                .that()
                .resideInAPackage("com.opsflow..")
                .should()
                .accessField(System.class, "out")
                .orShould()
                .accessField(System.class, "err")
                .allowEmptyShould(true)
                .because("structured logging via SLF4J is the only supported output;"
                        + " a println is invisible to log aggregation")
                .check(classes);
    }
}
