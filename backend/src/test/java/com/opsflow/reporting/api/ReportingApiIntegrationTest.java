/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.platform.security.JwtRoleConverter;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.support.IntegrationTest;
import java.time.LocalDate;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class ReportingApiIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";
    private static final LocalDate STARTS = LocalDate.of(2026, 1, 1);
    private static final LocalDate ENDS = LocalDate.of(2026, 3, 31);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 4, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricCommandService metricCommands;

    @Autowired
    private PeriodCommandService periodCommands;

    @Autowired
    private OrganizationCommandService organizationCommands;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
        jdbc.execute(
                """
                TRUNCATE opsflow.reporting_period_metric, opsflow.reporting_period_organization,
                         opsflow.reporting_period, opsflow.metric_definition,
                         opsflow.org_membership, opsflow.organization CASCADE
                """);
    }

    private static Jwt token(String subject, String username, List<String> realmRoles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .claim("preferred_username", username)
                .claim("name", username + " Person")
                .claim("realm_access", Map.of("roles", realmRoles))
                .audience(List.of("opsflow-api"))
                .build();
    }

    private static JwtRequestPostProcessor as(Jwt token) {
        return jwt().jwt(token)
                .authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }

    private static Jwt admin() {
        return token("admin-subject", "ada", List.of("USER", "ADMIN"));
    }

    private static Jwt plainUser() {
        return token("user-subject", "ria", List.of("USER"));
    }

    private UUID metric(String code) {
        return metricCommands
                .create(new MetricCode(code), code + " name", null, "unit", ValueType.DECIMAL, NumericRange.UNBOUNDED)
                .id()
                .value();
    }

    private UUID organization(String code) {
        return organizationCommands
                .create(new OrganizationCode(code), code + " name")
                .id()
                .value();
    }

    private UUID period(String code, LocalDate starts, LocalDate ends) {
        return periodCommands
                .create(new PeriodCode(code), PeriodType.QUARTER, starts, ends, ends.plusDays(15))
                .id()
                .value();
    }

    private void setScopes(UUID periodId, UUID metricId, UUID organizationId, long version) throws Exception {
        mockMvc.perform(put("/api/v1/periods/" + periodId + "/metrics")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"metricId\":\"" + metricId
                                + "\",\"required\":true,\"displayOrder\":0}],\"version\":" + version + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/periods/" + periodId + "/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"organizationIds\":[\"" + organizationId + "\"],\"version\":" + (version + 1) + "}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ authorization

    @Test
    void reportingEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/periods")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdministratorMayManageTheCatalogue() throws Exception {
        mockMvc.perform(
                        post("/api/v1/metrics")
                                .with(as(plainUser()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"HEADCOUNT","name":"Headcount","unit":"people","valueType":"INTEGER"}"""))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(plainUser()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"2026-Q1","periodType":"QUARTER","startsOn":"2026-01-01",
                                 "endsOn":"2026-03-31","submissionDeadline":"2026-04-15"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyAuthenticatedUserMayReadTheCatalogue() throws Exception {
        metric("HEADCOUNT");

        mockMvc.perform(get("/api/v1/metrics").with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------ uniqueness

    @Test
    void duplicateMetricCodesAreRejected() throws Exception {
        metric("HEADCOUNT");

        mockMvc.perform(
                        post("/api/v1/metrics")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"HEADCOUNT","name":"Again","unit":"people","valueType":"INTEGER"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void duplicatePeriodCodesAreRejected() throws Exception {
        period("2026-Q1", STARTS, ENDS);

        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"2026-Q1","periodType":"YEAR","startsOn":"2027-01-01",
                                 "endsOn":"2027-12-31","submissionDeadline":"2028-01-15"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void overlappingPeriodsOfTheSameTypeAreRejectedByTheDatabase() throws Exception {
        period("2026-Q1", STARTS, ENDS);

        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"2026-Q1B","periodType":"QUARTER","startsOn":"2026-03-01",
                                 "endsOn":"2026-05-31","submissionDeadline":"2026-06-15"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_OVERLAP"));
    }

    @Test
    void periodsOfDifferentTypesMayOverlap() throws Exception {
        period("2026-Q1", STARTS, ENDS);

        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"2026-FY","periodType":"YEAR","startsOn":"2026-01-01",
                                 "endsOn":"2026-12-31","submissionDeadline":"2027-01-15"}"""))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ validation

    @Test
    void rejectsADeadlineBeforeThePeriodEnds() throws Exception {
        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"BAD-DL","periodType":"MONTH","startsOn":"2026-06-01",
                                 "endsOn":"2026-06-30","submissionDeadline":"2026-06-15"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_DEADLINE_INVALID"));
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(
                        post("/api/v1/periods")
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"code":"BAD-RANGE","periodType":"MONTH","startsOn":"2026-06-30",
                                 "endsOn":"2026-06-01","submissionDeadline":"2026-07-15"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_DATES_INVALID"));
    }

    // ------------------------------------------------------------ scope rules

    @Test
    void anArchivedMetricCannotEnterAPeriodScope() throws Exception {
        UUID metricId = metric("RETIRED");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        mockMvc.perform(post("/api/v1/metrics/" + metricId + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/periods/" + periodId + "/metrics")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"metricId\":\"" + metricId
                                + "\",\"required\":true,\"displayOrder\":0}],\"version\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("METRIC_NOT_ACTIVE"));
    }

    @Test
    void anArchivedOrganizationCannotParticipate() throws Exception {
        UUID organizationId = organization("CLOSING");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        mockMvc.perform(post("/api/v1/organizations/" + organizationId + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/periods/" + periodId + "/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationIds\":[\"" + organizationId + "\"],\"version\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_ACTIVE"));
    }

    @Test
    void openingRequiresBothScopes() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID periodId = period("2026-Q1", STARTS, ENDS);

        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_SCOPE_INCOMPLETE"));

        mockMvc.perform(put("/api/v1/periods/" + periodId + "/metrics")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"metricId\":\"" + metricId
                                + "\",\"required\":true,\"displayOrder\":0}],\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_SCOPE_INCOMPLETE"));
    }

    @Test
    void neitherScopeCanChangeAfterOpening() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID otherMetric = metric("UPTIME");
        UUID organizationId = organization("BERLIN_OPS");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, organizationId, 0);

        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").exists());

        mockMvc.perform(put("/api/v1/periods/" + periodId + "/metrics")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"metricId\":\"" + otherMetric
                                + "\",\"required\":true,\"displayOrder\":0}],\"version\":3}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_SCOPE_FROZEN"));

        mockMvc.perform(put("/api/v1/periods/" + periodId + "/organizations")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationIds\":[\"" + organization("MADRID_OPS") + "\"],\"version\":3}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_SCOPE_FROZEN"));
    }

    @Test
    void laterOrganizationChangesDoNotMutateAFrozenParticipantSet() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID participant = organization("BERLIN_OPS");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, participant, 0);
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk());

        // Created after the freeze, and one participant archived afterwards.
        organization("MADRID_OPS");
        mockMvc.perform(post("/api/v1/organizations/" + participant + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/periods/" + periodId).with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.organizations[0].organizationId").value(participant.toString()))
                .andExpect(jsonPath("$.organizations[0].active").value(false));

        assertThat(participantCount(periodId)).isEqualTo(1);
    }

    @Test
    void closingAnOpenPeriodSucceedsAndCannotBeRepeated() throws Exception {
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metric("HEADCOUNT"), organization("BERLIN_OPS"), 0);
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/periods/" + periodId + "/close")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/periods/" + periodId + "/close")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":4}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void aMetricCollectedByAnOpenPeriodCannotBeArchived() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, organization("BERLIN_OPS"), 0);
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk());

        // The period's scope is frozen, so archiving would leave it collecting something the
        // catalogue says no longer exists.
        mockMvc.perform(post("/api/v1/metrics/" + metricId + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("METRIC_IN_USE"));
    }

    @Test
    void aMetricUsedOnlyByDraftPeriodsCanStillBeArchived() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        mockMvc.perform(put("/api/v1/periods/" + periodId + "/metrics")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[{\"metricId\":\"" + metricId
                                + "\",\"required\":true,\"displayOrder\":0}],\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/metrics/" + metricId + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void aMetricUsedOnlyByClosedPeriodsCanBeArchived() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, organization("BERLIN_OPS"), 0);
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/close")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/metrics/" + metricId + "/archive")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void aStaleVersionLosesOnAPeriod() throws Exception {
        UUID periodId = period("2026-Q1", STARTS, ENDS);

        mockMvc.perform(
                        patch("/api/v1/periods/" + periodId)
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"startsOn":"2026-01-01","endsOn":"2026-03-30",
                                 "submissionDeadline":"2026-04-15","version":0}"""))
                .andExpect(status().isOk());

        mockMvc.perform(
                        patch("/api/v1/periods/" + periodId)
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"startsOn":"2026-01-01","endsOn":"2026-03-29",
                                 "submissionDeadline":"2026-04-15","version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void aStaleVersionLosesOnAMetric() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        String body = """
                {"name":"Renamed","unit":"people","version":0}""";

        mockMvc.perform(patch("/api/v1/metrics/" + metricId)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/metrics/" + metricId)
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void metricBoundsCannotChangeOnceThePeriodUsingThemHasOpened() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, organization("BERLIN_OPS"), 0);
        mockMvc.perform(post("/api/v1/periods/" + periodId + "/open")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}"""))
                .andExpect(status().isOk());

        mockMvc.perform(
                        patch("/api/v1/metrics/" + metricId)
                                .with(as(admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"HEADCOUNT name","unit":"unit","minValue":0,"maxValue":10,"version":0}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("METRIC_IN_USE"));
    }

    @Test
    void thePeriodDetailReportsItsScope() throws Exception {
        UUID metricId = metric("HEADCOUNT");
        UUID organizationId = organization("BERLIN_OPS");
        UUID periodId = period("2026-Q1", STARTS, ENDS);
        setScopes(periodId, metricId, organizationId, 0);

        mockMvc.perform(get("/api/v1/periods/" + periodId).with(as(plainUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.code").value("2026-Q1"))
                .andExpect(jsonPath("$.metrics", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.metrics[0].code").value("HEADCOUNT"))
                .andExpect(jsonPath("$.metrics[0].required").value(true))
                .andExpect(jsonPath("$.organizations", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.organizations[0].code").value("BERLIN_OPS"));
    }

    private int participantCount(UUID periodId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM opsflow.reporting_period_organization WHERE reporting_period_id = ?",
                Integer.class,
                periodId);
    }
}
