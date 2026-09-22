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
 * Plain Jupiter tests calling {@code ArchRule.check(...)} rather than {@code @AnalyzeClasses},
 * whose JUnit Platform integration is unverified against Platform 6. {@code allowEmptyShould} is
 * set because ArchUnit fails a rule matching nothing.
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
