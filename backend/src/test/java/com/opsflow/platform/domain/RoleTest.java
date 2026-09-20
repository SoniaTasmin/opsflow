/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoleTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void authorityUsesSpringSecuritysRolePrefix(Role role) {
        assertThat(role.authority()).isEqualTo("ROLE_" + role.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "user", "Reviewer", "ADMIN"})
    void recognisesItsOwnRealmRolesRegardlessOfCase(String realmRole) {
        assertThat(Role.fromRealmRole(realmRole)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline_access", "uma_authorization", "default-roles-opsflow", "SOME_OTHER_APP_ROLE"})
    void ignoresRealmRolesThatBelongToSomethingElse(String foreignRole) {
        // Keycloak issues roles of its own, and a realm may carry roles for other
        // applications. An authority this application does not understand is one it
        // cannot reason about, so it never becomes one.
        assertThat(Role.fromRealmRole(foreignRole)).isEmpty();
    }

    @Test
    void ignoresNull() {
        assertThat(Role.fromRealmRole(null)).isEmpty();
    }
}
