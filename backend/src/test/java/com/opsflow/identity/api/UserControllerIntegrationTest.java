/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.platform.security.JwtRoleConverter;
import com.opsflow.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
        jdbc.execute("TRUNCATE opsflow.org_membership, opsflow.organization, opsflow.app_user CASCADE");

        // Provisioning is lazy, and the frontend calls /me at bootstrap, so anyone reaching an
        // admin screen has been provisioned by then.
        mockMvc.perform(get("/api/v1/me").with(as(admin()))).andExpect(status().isOk());
        provision("alice-subject", "alice", "Alice Anderson");
        provision("bob-subject", "bob", "Bob Brown");
        provision("carol-subject", "carol", "Carol Clark");
        provision("dave-subject", "dave", "Dave Dawson");
    }

    private void provision(String subject, String username, String displayName) throws Exception {
        mockMvc.perform(get("/api/v1/me").with(as(token(subject, username, displayName, List.of("USER")))))
                .andExpect(status().isOk());
    }

    private static Jwt token(String subject, String username, String displayName, List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .claim("preferred_username", username)
                .claim("name", displayName)
                .claim("email", username + "@example.com")
                .claim("realm_access", Map.of("roles", roles))
                .audience(List.of("opsflow-api"))
                .build();
    }

    private static JwtRequestPostProcessor as(Jwt token) {
        return jwt().jwt(token)
                .authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }

    private static Jwt admin() {
        return token("admin-subject", "ada", "Ada Admin", List.of("USER", "ADMIN"));
    }

    private static Jwt plainUser() {
        return token("user-subject", "ria", "Ria Reporter", List.of("USER"));
    }

    private static Jwt reviewer() {
        return token("reviewer-subject", "rex", "Rex Reviewer", List.of("USER", "REVIEWER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void isDeniedToNonAdministrators() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(as(plainUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/users").with(as(reviewer()))).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorSeesProvisionedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].username", Matchers.hasItem("alice")));
    }

    @Test
    void matchesUsernameAndDisplayNameCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("q", "ALI").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("alice"));

        mockMvc.perform(get("/api/v1/users").param("q", "brown").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].displayName").value("Bob Brown"));
    }

    @Test
    void returnsNothingWhenNoUserMatches() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("q", "nobody-by-that-name").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesDeterministically() throws Exception {
        String firstPage = mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "2")
                        .with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                // Ordered by username: ada, alice, bob, carol, dave.
                .andExpect(jsonPath("$.content[0].username").value("ada"))
                .andExpect(jsonPath("$.content[1].username").value("alice"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .param("size", "2")
                        .with(as(admin())))
                .andExpect(jsonPath("$.content[0].username").value("bob"))
                .andExpect(jsonPath("$.content[1].username").value("carol"));

        String repeated = mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "2")
                        .with(as(admin())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assertions.assertThat(repeated).isEqualTo(firstPage);
    }

    @Test
    void exposesNothingSensitive() throws Exception {
        String body = mockMvc.perform(get("/api/v1/users").param("q", "alice").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].roles").doesNotExist())
                .andExpect(jsonPath("$.content[0].oidcSubject").doesNotExist())
                .andExpect(jsonPath("$.content[0].oidcIssuer").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertThat(body)
                .doesNotContain("alice-subject")
                .doesNotContain(ISSUER)
                .doesNotContain("alice@example.com");
    }

    @Test
    void rejectsAnOversizedQueryOrPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("q", "x".repeat(101)).with(as(admin())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users").param("size", "1000").with(as(admin())))
                .andExpect(status().isBadRequest());
    }
}
