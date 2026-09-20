/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.platform.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    private static Jwt tokenWithRealmRoles(Object rolesClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (rolesClaim != null) {
            builder.claim("realm_access", Map.of("roles", rolesClaim));
        }
        return builder.build();
    }

    @Test
    void mapsApplicationRealmRolesToAuthorities() {
        Jwt jwt = tokenWithRealmRoles(List.of("USER", "REVIEWER"));

        var authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_REVIEWER");
    }

    @Test
    void ignoresRolesBelongingToKeycloakOrOtherApplications() {
        Jwt jwt = tokenWithRealmRoles(List.of("USER", "offline_access", "uma_authorization", "SOME_OTHER_ROLE"));

        assertThat(JwtRoleConverter.rolesOf(jwt)).containsExactly(Role.USER);
    }

    @Test
    void aTokenWithNoRecognisedRoleYieldsNoAuthorities() {
        // Authenticated but entitled to nothing. Every authorization check then fails
        // closed, which is the only safe reading of "this user has no roles".
        Jwt jwt = tokenWithRealmRoles(List.of("offline_access"));

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void toleratesAMissingRealmAccessClaim() {
        assertThat(JwtRoleConverter.rolesOf(tokenWithRealmRoles(null))).isEmpty();
    }

    @Test
    void toleratesARealmAccessClaimOfAnUnexpectedShape() {
        // A malformed claim must not throw on the authentication path, where an
        // exception would be far harder to diagnose than an empty role set.
        assertThat(JwtRoleConverter.rolesOf(tokenWithRealmRoles("not-a-list"))).isEmpty();
    }
}
