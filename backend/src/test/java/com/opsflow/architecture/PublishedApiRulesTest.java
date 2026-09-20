/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What a module may show to the rest of the application, and in what terms.
 *
 * <p>The module dependency <em>order</em> is enforced elsewhere; these rules govern the
 * <em>surface</em>: which package other modules may reach into, and whether the types crossing
 * that boundary are meaningful or merely structural.
 */
class PublishedApiRulesTest {

    private static final List<String> MODULES =
            List.of("identity", "organization", "reporting", "submission", "imports");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void modulesTalkToEachOtherOnlyThroughTheirPublishedRootPackage() {
        // A module's root package is its contract; domain, application, infrastructure and
        // api are its internals. Without this rule the layered-architecture check still
        // passes while another module reaches straight into an aggregate — at which point
        // the boundary is decorative.
        for (String module : MODULES) {
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.opsflow." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.opsflow." + module + ".domain..",
                            "com.opsflow." + module + ".application..",
                            "com.opsflow." + module + ".infrastructure..",
                            "com.opsflow." + module + ".api..")
                    .allowEmptyShould(true)
                    .because("com.opsflow." + module + " is the published API; everything below it is internal")
                    .check(classes);
        }
    }

    @Test
    void publishedModuleApisUseTypedIdentifiersRatherThanRawUuids() {
        // Every identifier in this system is a UUID underneath, so a raw one in a contract
        // is a parameter the compiler cannot check: passing an organization id where a user
        // id belongs compiles perfectly and fails at runtime, or silently does the wrong
        // thing. Typed ids make that a compile error.
        //
        // Unwrapping to UUID is legitimate at the two edges — persistence and JSON — which
        // live in infrastructure and api, below the published surface this rule covers.
        for (String module : MODULES) {
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("com.opsflow." + module)
                    .and()
                    // The typed identifiers themselves are exactly the types that wrap and
                    // unwrap a UUID; excluding them is what makes the rule about everything
                    // else meaningful.
                    .areDeclaredInClassesThat()
                    .haveSimpleNameNotEndingWith("Id")
                    .and()
                    .arePublic()
                    .should(notExposeRawUuids())
                    .allowEmptyShould(true)
                    .because("a raw UUID in a module contract is an unchecked parameter")
                    .check(classes);
        }
    }

    private static ArchCondition<JavaMethod> notExposeRawUuids() {
        return new ArchCondition<>("not expose raw java.util.UUID") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean returnsUuid = method.getRawReturnType().isEquivalentTo(UUID.class);
                boolean takesUuid = method.getRawParameterTypes().stream()
                        .anyMatch(parameter -> parameter.isEquivalentTo(UUID.class));

                if (returnsUuid || takesUuid) {
                    events.add(SimpleConditionEvent.violated(
                            method, method.getFullName() + " exposes a raw UUID; use the module's typed identifier"));
                }
            }
        };
    }
}
