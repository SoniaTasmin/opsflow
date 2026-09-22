/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
class OrganizationControllerIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationCommandService commands;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
        jdbc.execute("TRUNCATE opsflow.org_membership, opsflow.organization CASCADE");
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

    /**
     * The bare {@code jwt()} post-processor derives authorities from the {@code scope} claim, so
     * realm roles would never become {@code ROLE_*} and every deny case would pass for the wrong
     * reason.
     */
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            as(Jwt token) {
        return jwt().jwt(token)
                .authorities(new com.opsflow.platform.security.JwtRoleConverter()
                        .convert(token)
                        .getAuthorities());
    }

    private static Jwt admin() {
        return token("admin-subject", "ada", List.of("USER", "ADMIN"));
    }

    private static Jwt plainUser() {
        return token("user-subject", "ria", List.of("USER"));
    }

    private static Jwt reviewer() {
        return token("reviewer-subject", "rex", List.of("USER", "REVIEWER"));
    }

    private UUID provision(Jwt jwt) throws Exception {
        String body = mockMvc.perform(get("/api/v1/me").with(as(jwt)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    @Transactional
    UUID createOrganization(String code, String name) {
        return commands.create(new OrganizationCode(code), name).id().value();
    }

    @Test
    void everyOrganizationEndpointRequiresAuthentication() throws Exception {
        UUID someId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/organizations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/organizations/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/organizations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/organizations/" + someId + "/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdministratorMayCreateAnOrganization() throws Exception {
        String body = """
                {"code":"BERLIN_OPS","name":"Berlin Operations"}""";

        mockMvc.perform(post("/api/v1/organizations")
                        .with(as(plainUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BERLIN_OPS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.memberCount").value(0));
    }

    @Test
    void aReviewerMayNotAdministerOrganizations() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .with(as(reviewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NOPE","name":"Not Allowed"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserSeesOnlyTheOrganizationsTheyBelongTo() throws Exception {
        UUID mine = createOrganization("MINE", "My Organization");
        createOrganization("THEIRS", "Someone Else");
        UUID userId = provision(plainUser());
        mockMvc.perform(put("/api/v1/organizations/" + mine + "/members/" + userId)
                        .with(as(admin())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations").with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("MINE"))
                // A count of 2 here would disclose an organization the caller cannot see.
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anAdministratorAndAReviewerSeeEveryOrganization() throws Exception {
        createOrganization("ALPHA", "Alpha");
        createOrganization("BETA", "Beta");

        for (Jwt privileged : List.of(admin(), reviewer())) {
            mockMvc.perform(get("/api/v1/organizations").with(as(privileged)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    @Test
    void anOrganizationTheCallerCannotSeeIsReportedAsMissingRatherThanForbidden() throws Exception {
        UUID invisible = createOrganization("SECRET", "Not Yours");

        // 403 would confirm the organization exists.
        mockMvc.perform(get("/api/v1/organizations/" + invisible).with(as(plainUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void aMemberMaySeeTheirOwnOrganizationAndItsMembers() throws Exception {
        UUID organization = createOrganization("VISIBLE", "Visible");
        UUID userId = provision(plainUser());
        mockMvc.perform(put("/api/v1/organizations/" + organization + "/members/" + userId)
                .with(as(admin())));

        mockMvc.perform(get("/api/v1/organizations/" + organization).with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VISIBLE"));

        mockMvc.perform(get("/api/v1/organizations/" + organization + "/members")
                        .with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void addingAMemberIsIdempotent() throws Exception {
        UUID organization = createOrganization("IDEMPOTENT", "Idempotent");
        UUID userId = provision(plainUser());
        String path = "/api/v1/organizations/" + organization + "/members/" + userId;

        mockMvc.perform(put(path).with(as(admin()))).andExpect(status().isCreated());
        mockMvc.perform(put(path).with(as(admin()))).andExpect(status().isNoContent());

        assertThat(memberCount(organization)).isEqualTo(1);
    }

    @Test
    void removingAMemberIsIdempotent() throws Exception {
        UUID organization = createOrganization("REMOVE", "Remove");
        UUID userId = provision(plainUser());
        String path = "/api/v1/organizations/" + organization + "/members/" + userId;
        mockMvc.perform(put(path).with(as(admin())));

        mockMvc.perform(delete(path).with(as(admin()))).andExpect(status().isNoContent());
        mockMvc.perform(delete(path).with(as(admin()))).andExpect(status().isNoContent());

        assertThat(memberCount(organization)).isZero();
    }

    @Test
    void addingAnUnknownUserIsReportedAsMissing() throws Exception {
        UUID organization = createOrganization("UNKNOWN", "Unknown");

        mockMvc.perform(put("/api/v1/organizations/" + organization + "/members/" + UUID.randomUUID())
                        .with(as(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdministratorMayChangeMembership() throws Exception {
        UUID organization = createOrganization("GUARDED", "Guarded");
        UUID userId = provision(plainUser());
        String path = "/api/v1/organizations/" + organization + "/members/" + userId;

        mockMvc.perform(put(path).with(as(plainUser()))).andExpect(status().isForbidden());
        mockMvc.perform(delete(path).with(as(reviewer()))).andExpect(status().isForbidden());
    }

    @Test
    void archivingStopsFurtherMembershipButPermitsRemoval() throws Exception {
        UUID organization = createOrganization("CLOSING", "Closing Down");
        UUID member = provision(plainUser());
        String memberPath = "/api/v1/organizations/" + organization + "/members/" + member;
        mockMvc.perform(put(memberPath).with(as(admin())));

        mockMvc.perform(post("/api/v1/organizations/" + organization + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").exists());

        UUID newcomer = provision(reviewer());
        mockMvc.perform(put("/api/v1/organizations/" + organization + "/members/" + newcomer)
                        .with(as(admin())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_ARCHIVED"));

        mockMvc.perform(delete(memberPath).with(as(admin()))).andExpect(status().isNoContent());
    }

    @Test
    void archivingTwiceIsAConflict() throws Exception {
        UUID organization = createOrganization("TWICE", "Twice");
        String path = "/api/v1/organizations/" + organization + "/archive";
        String body = """
                {"version":0}""";

        mockMvc.perform(post(path)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(path)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void aStaleVersionLosesRatherThanSilentlyOverwriting() throws Exception {
        UUID organization = createOrganization("CONTESTED", "Original Name");

        mockMvc.perform(patch("/api/v1/organizations/" + organization)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"First Rename","version":0}"""))
                .andExpect(status().isOk());

        // Second caller read version 0 before the first wrote.
        mockMvc.perform(patch("/api/v1/organizations/" + organization)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second Rename","version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void duplicateCodesAreRejected() throws Exception {
        createOrganization("UNIQUE", "First");

        mockMvc.perform(post("/api/v1/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"UNIQUE","name":"Second"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void anInvalidCodeIsRejectedBeforeAnyBusinessLogicRuns() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"lower case","name":"Invalid"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void meReportsTheCallersOrganizationsWithoutAnyRole() throws Exception {
        UUID organization = createOrganization("MY_ORG", "My Organization");
        UUID userId = provision(plainUser());
        mockMvc.perform(put("/api/v1/organizations/" + organization + "/members/" + userId)
                .with(as(admin())));

        mockMvc.perform(get("/api/v1/me").with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.contains("USER")))
                .andExpect(jsonPath("$.organizations", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.organizations[0].id").value(organization.toString()))
                .andExpect(jsonPath("$.organizations[0].code").value("MY_ORG"))
                .andExpect(jsonPath("$.organizations[0].active").value(true))
                .andExpect(jsonPath("$.organizations[0].role").doesNotExist());
    }

    @Test
    void meReportsNoOrganizationsForSomeoneWhoBelongsToNone() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(as(reviewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations").isEmpty());
    }

    private int memberCount(UUID organizationId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM opsflow.org_membership WHERE organization_id = ?", Integer.class, organizationId);
    }
}
