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

/** The module dependency order is enforced elsewhere; these rules govern the surface. */
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
        // Without this, the layered check still passes while another module reaches
        // straight into an aggregate.
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
        // Every identifier here is a UUID underneath, so a raw one in a contract is a
        // parameter the compiler cannot check. Unwrapping is legitimate at the persistence
        // and JSON edges, which sit below this surface.
        for (String module : MODULES) {
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAPackage("com.opsflow." + module)
                    .and()
                    // The identifier types are the ones that legitimately wrap a UUID.
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
