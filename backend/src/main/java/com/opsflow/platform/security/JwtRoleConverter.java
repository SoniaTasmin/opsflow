/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import com.opsflow.platform.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Only this application's own roles become authorities; a realm carries others. A token with no
 * recognised role authenticates with no authorities, so every check fails closed.
 */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesOf(jwt));
    }

    public static Set<Role> rolesOf(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Set.of();
        }
        Object roles = realmAccess.get(ROLES_CLAIM);
        if (!(roles instanceof Collection<?> roleNames)) {
            return Set.of();
        }
        return roleNames.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(Role::fromRealmRole)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<GrantedAuthority> authoritiesOf(Jwt jwt) {
        return rolesOf(jwt).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
