/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Rules that keep the identity decisions in ADR-0003 true as the codebase grows.
 *
 * <p>Each of these encodes a commitment that is easy to erode with one convenient shortcut, and
 * whose erosion would not show up as a failing feature test.
 */
class IdentityBoundaryRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.opsflow");
    }

    @Test
    void theExternalIdentityNeverLeavesTheIdentityModule() {
        // How a person is recognised upstream is the identity module's business. If
        // ExternalIdentity spreads, the OIDC subject starts appearing in other modules'
        // signatures and eventually in a foreign key — which is precisely the coupling
        // the application-owned UserId exists to prevent.
        noClasses()
                .that()
                .resideOutsideOfPackage("com.opsflow.identity..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.opsflow.identity.domain.ExternalIdentity")
                .allowEmptyShould(true)
                .because("the OIDC issuer and subject are internal to identity (ADR-0003)")
                .check(classes);
    }

    @Test
    void noModuleOutsideIdentityReadsRawTokens() {
        // Business code must depend on Actor and its roles, never on the token those
        // roles came from. Reaching for the Jwt elsewhere is how claim-parsing logic
        // ends up duplicated and inconsistent across modules.
        noClasses()
                .that()
                .resideOutsideOfPackage("com.opsflow.identity..")
                .and()
                .resideOutsideOfPackage("com.opsflow.platform.security..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.security.oauth2.jwt..")
                .allowEmptyShould(true)
                .because("authorization is expressed with Actor and Role; only identity and the"
                        + " security configuration deal in tokens")
                .check(classes);
    }

    @Test
    void thePersistedUserCarriesNoRoles() {
        // The database must never become a second source of truth for roles. A stored
        // role is a privilege-escalation bug with a delay fuse: it keeps granting access
        // after the identity provider has revoked it.
        noClasses()
                .that()
                .resideInAPackage("com.opsflow.identity.domain..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.opsflow.platform.domain.Role")
                .allowEmptyShould(true)
                .because("roles come from the token on every request and are never persisted (ADR-0003)")
                .check(classes);
    }

    @Test
    void membershipCarriesNoRole() {
        // A role here would mean two sources of truth for authorization again.
        noClasses()
                .that()
                .resideInAPackage("com.opsflow.organization.domain..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.opsflow.platform.domain.Role")
                .allowEmptyShould(true)
                .because("membership is a set of users and carries no role of its own (ADR-0003)")
                .check(classes);
    }

    @Test
    void nothingPersistsCredentialsOrTokens() {
        noClasses()
                .that()
                .resideInAPackage("com.opsflow..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.security.oauth2.core.OAuth2AccessToken")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.security.oauth2.core.OAuth2RefreshToken")
                .allowEmptyShould(true)
                .because("access and refresh tokens are never stored or handled by this application")
                .check(classes);
    }
}
