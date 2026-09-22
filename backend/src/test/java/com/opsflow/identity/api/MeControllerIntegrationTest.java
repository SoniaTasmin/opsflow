/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The authentication contract for a protected endpoint, exercised through the real filter chain.
 *
 * <p>The {@code JwtDecoder} is replaced so the context does not need to reach a live identity
 * provider to start. Token <em>validation</em> — signature, issuer, expiry, audience — is covered
 * by its own unit tests and end to end against a real Keycloak in
 * {@code KeycloakAuthenticationIntegrationTest}.
 */
@IntegrationTest
@AutoConfigureMockMvc
class MeControllerIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";

    // The real filter chain: whether a 401 carries a correlation id depends on filter order.
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // An unstubbed Mockito mock returns null, and Spring Security treats a null Jwt as a
        // successfully decoded one - which would make a garbage token authenticate. Rejecting
        // is what a real decoder does with a token it cannot verify.
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
    }

    private static Jwt token(String subject, String username, List<String> realmRoles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .claim("preferred_username", username)
                .claim("name", username + " Person")
                .claim("email", username + "@example.com")
                .claim("realm_access", Map.of("roles", realmRoles))
                .audience(List.of("opsflow-api"))
                .build();
    }

    @Test
    void rejectsARequestWithNoTokenAtAll() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").value(Matchers.notNullValue()));
    }

    @Test
    void rejectsAGarbageBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void doesNotExplainWhyAuthenticationFailed() throws Exception {
        // Telling a caller precisely why their token was rejected helps them craft a
        // better one. The reason is logged instead.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
    }

    @Test
    void describesTheCallerAndProvisionsThemOnFirstRequest() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token("new-subject", "newcomer", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Matchers.notNullValue()))
                .andExpect(jsonPath("$.username").value("newcomer"))
                .andExpect(jsonPath("$.displayName").value("newcomer Person"))
                .andExpect(jsonPath("$.email").value("newcomer@example.com"))
                .andExpect(jsonPath("$.roles").value(Matchers.contains("USER")));
    }

    @Test
    void neverExposesTheExternalIdentityToTheClient() throws Exception {
        // How a person is recognised upstream is an internal detail; nothing
        // client-side has a use for it.
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token("secret-subject-value", "hidden", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Matchers.not("secret-subject-value")))
                .andExpect(jsonPath("$.oidcSubject").doesNotExist())
                .andExpect(jsonPath("$.oidcIssuer").doesNotExist())
                .andExpect(jsonPath("$.subject").doesNotExist());
    }

    @Test
    void reportsEveryApplicationRoleTheTokenCarries() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token("multi-role", "boss", List.of("USER", "REVIEWER", "ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("USER", "REVIEWER", "ADMIN")));
    }

    @Test
    void anAuthenticatedUserWithNoRolesIsEntitledToNothing() throws Exception {
        // Authenticated, provisioned, and holding no authority whatsoever.
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token("no-roles", "nobody", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void ignoresRolesThatBelongToKeycloakOrOtherApplications() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token("foreign-roles", "foreigner", List.of("offline_access", "USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.contains("USER")));
    }

    @Test
    void theSameCallerKeepsTheSameApplicationIdentityAcrossRequests() throws Exception {
        Jwt jwt = token("stable-subject", "stable", List.of("USER"));

        String first = mockMvc.perform(get("/api/v1/me").with(jwt().jwt(jwt)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = mockMvc.perform(get("/api/v1/me").with(jwt().jwt(jwt)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second);
    }
}
