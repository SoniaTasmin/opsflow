/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enforces the module dependency order from the architecture plan:
 *
 * <pre>
 * platform -> identity -> organization -> reporting -> submission -> imports
 * </pre>
 *
 * <p>The order is total, so cycles are impossible by construction and any
 * back-edge fails the build. These rules exist from M0 — before there is
 * anything to violate — because boundary tests retrofitted onto an eroded
 * codebase never actually get written.
 *
 * <p>All six layers are declared from M0, before most of them exist.
 * {@code withOptionalLayers(true)} is required for that: without it ArchUnit
 * treats an empty layer as a violation in its own right, so the rule would fail
 * until the last module was written — and a rule that cannot pass is a rule
 * someone deletes.
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
                .whereLayer("imports")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("submission")
                .mayOnlyBeAccessedByLayers("imports")
                .whereLayer("reporting")
                .mayOnlyBeAccessedByLayers("submission", "imports")
                .whereLayer("organization")
                .mayOnlyBeAccessedByLayers("reporting", "submission", "imports")
                .whereLayer("identity")
                .mayOnlyBeAccessedByLayers("organization", "reporting", "submission", "imports")
                .because("the module order is platform -> identity -> organization -> reporting"
                        + " -> submission -> imports, and a back-edge would create a cycle")
                .check(classes);
    }
}
