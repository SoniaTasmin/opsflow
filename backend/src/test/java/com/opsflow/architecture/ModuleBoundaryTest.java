/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The module order is total, so cycles are impossible and any back-edge fails the build.
 * {@code withOptionalLayers(true)} is required because ArchUnit treats an empty layer as a
 * violation in itself.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void modulesRespectTheDependencyOrder() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("platform")
                .definedBy("com.opsflow.platform..")
                .layer("identity")
                .definedBy("com.opsflow.identity..")
                .layer("organization")
                .definedBy("com.opsflow.organization..")
                .layer("reporting")
                .definedBy("com.opsflow.reporting..")
                .layer("submission")
                .definedBy("com.opsflow.submission..")
                .layer("imports")
                .definedBy("com.opsflow.imports..")
                // Depends on every module; nothing depends on it.
                .layer("composition")
                .definedBy("com.opsflow.composition..")
                .whereLayer("composition")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("imports")
                .mayOnlyBeAccessedByLayers("composition")
                .whereLayer("submission")
                .mayOnlyBeAccessedByLayers("imports", "composition")
                .whereLayer("reporting")
                .mayOnlyBeAccessedByLayers("submission", "imports", "composition")
                .whereLayer("organization")
                .mayOnlyBeAccessedByLayers("reporting", "submission", "imports", "composition")
                .whereLayer("identity")
                .mayOnlyBeAccessedByLayers("organization", "reporting", "submission", "imports", "composition")
                .because("the module order is platform -> identity -> organization -> reporting"
                        + " -> submission -> imports -> composition, and a back-edge would create a cycle")
                .check(classes);
    }
}
