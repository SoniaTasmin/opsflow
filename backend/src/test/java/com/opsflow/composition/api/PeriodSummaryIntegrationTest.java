/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.platform.security.JwtRoleConverter;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * The summary against a real database, and the property that makes it worth having: its figures
 * are fixed to what the period froze, not to what exists today.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PeriodSummaryIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationCommandService organizationCommands;

    @Autowired
    private MetricCommandService metricCommands;

    @Autowired
    private PeriodCommandService periodCommands;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private OrganizationId alpha;
    private OrganizationId beta;
    private OrganizationId gamma;
    private PeriodId period;
    private String summaryPath;
    private UserId reporterId;
    private UserId reviewerId;

    @BeforeEach
    void setUp() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
        jdbc.execute(
                """
                TRUNCATE opsflow.import_row_error, opsflow.import_job,
                         opsflow.submission_transition, opsflow.submission_value, opsflow.submission,
                         opsflow.reporting_period_metric, opsflow.reporting_period_organization,
                         opsflow.reporting_period, opsflow.metric_definition,
                         opsflow.org_membership, opsflow.organization, opsflow.app_user CASCADE
                """);

        alpha = organizationCommands
                .create(new OrganizationCode("ALPHA"), "Alpha unit")
                .id();
        beta = organizationCommands
                .create(new OrganizationCode("BETA"), "Beta unit")
                .id();
        gamma = organizationCommands
                .create(new OrganizationCode("GAMMA"), "Gamma unit")
                .id();

        MetricId headcount = metricCommands
                .create(
                        new MetricCode("HEADCOUNT"),
                        "Headcount",
                        null,
                        "people",
                        ValueType.INTEGER,
                        new NumericRange(BigDecimal.ZERO, new BigDecimal("1000")))
                .id();
        period = openPeriod(headcount, Set.of(alpha, beta, gamma));
        summaryPath = "/api/v1/periods/" + period + "/summary";

        reporterId = provision(reporter());
        reviewerId = provision(reviewer());
        provision(admin());
        provision(stranger());
        organizationCommands.addMember(alpha, reporterId);
    }

    // ------------------------------------------------------------ counting

    @Test
    void theFrozenParticipantSetIsTheDenominatorAndNobodyHasStarted() throws Exception {
        mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.participatingOrganizations").value(3))
                .andExpect(jsonPath("$.notStarted").value(3))
                .andExpect(jsonPath("$.approved").value(0))
                .andExpect(jsonPath("$.completionRatio").value(0.0));
    }

    @Test
    void everyWorkflowStatusIsCountedAndTheyReconcile() throws Exception {
        submission(alpha, SubmissionStatus.DRAFT);
        submission(beta, SubmissionStatus.UNDER_REVIEW);
        submission(gamma, SubmissionStatus.APPROVED);

        MvcResult result = mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.notStarted").value(0))
                .andExpect(jsonPath("$.draft").value(1))
                .andExpect(jsonPath("$.underReview").value(1))
                .andExpect(jsonPath("$.approved").value(1))
                .andReturn();

        assertReconciles(result);
    }

    @Test
    void submittedAndRejectedAreCountedSeparately() throws Exception {
        submission(alpha, SubmissionStatus.SUBMITTED);
        submission(beta, SubmissionStatus.REJECTED);

        MvcResult result = mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.submitted").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.notStarted").value(1))
                .andReturn();

        assertReconciles(result);
    }

    @Test
    void approvingASubmissionMovesTheFigures() throws Exception {
        submission(alpha, SubmissionStatus.UNDER_REVIEW);
        mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.approved").value(0));

        jdbc.update(
                """
                UPDATE opsflow.submission
                   SET status = 'APPROVED', reviewer_id = NULL, decided_at = now(),
                       decided_by = reviewer_id, version = version + 1
                 WHERE organization_id = ?
                """,
                alpha.value());

        mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.underReview").value(0))
                .andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.completionRatio").value(1.0 / 3));
    }

    // ------------------------------------------------------------ frozen scope

    @Test
    void anOrganizationCreatedAfterThePeriodOpenedDoesNotAppear() throws Exception {
        submission(alpha, SubmissionStatus.APPROVED);
        organizationCommands.create(new OrganizationCode("DELTA"), "Delta unit");

        mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.participatingOrganizations").value(3))
                .andExpect(jsonPath("$.notStarted").value(2));
    }

    /** It did participate, so removing it from the figures would rewrite history. */
    @Test
    void aParticipantArchivedAfterThePeriodOpenedStillCounts() throws Exception {
        long version =
                jdbc.queryForObject("SELECT version FROM opsflow.organization WHERE id = ?", Long.class, gamma.value());
        organizationCommands.archive(gamma, version);

        mockMvc.perform(get(summaryPath).with(as(admin())))
                .andExpect(jsonPath("$.participatingOrganizations").value(3))
                .andExpect(jsonPath("$.notStarted").value(3));
    }

    // ------------------------------------------------------------ visibility

    @Test
    void aReporterSeesOnlyTheirOwnOrganizationInBothCountAndDenominator() throws Exception {
        submission(alpha, SubmissionStatus.APPROVED);
        submission(beta, SubmissionStatus.APPROVED);

        mockMvc.perform(get(summaryPath).with(as(reporter())))
                .andExpect(jsonPath("$.scope").value("MY_ORGANIZATIONS"))
                .andExpect(jsonPath("$.participatingOrganizations").value(1))
                .andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.completionRatio").value(1.0));
    }

    @Test
    void aReviewerSeesEveryParticipant() throws Exception {
        mockMvc.perform(get(summaryPath).with(as(reviewer())))
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.participatingOrganizations").value(3));
    }

    @Test
    void someoneWhoBelongsNowhereSeesAnEmptySummaryRatherThanEveryones() throws Exception {
        submission(alpha, SubmissionStatus.APPROVED);

        mockMvc.perform(get(summaryPath).with(as(stranger())))
                .andExpect(jsonPath("$.participatingOrganizations").value(0))
                .andExpect(jsonPath("$.approved").value(0))
                .andExpect(jsonPath("$.notStarted").value(0));
    }

    @Test
    void theSummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get(summaryPath)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownPeriodIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/periods/" + UUID.randomUUID() + "/summary").with(as(admin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ------------------------------------------------------------ helpers

    private void assertReconciles(MvcResult result) throws Exception {
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        int counted = List.of("notStarted", "draft", "submitted", "underReview", "rejected", "approved").stream()
                .mapToInt(field -> body.get(field).asInt())
                .sum();
        org.assertj.core.api.Assertions.assertThat(counted)
                .as("every participant is in exactly one bucket")
                .isEqualTo(body.get("participatingOrganizations").asInt());
    }

    /** Seeded directly, but only in states the schema's own constraints accept. */
    private void submission(OrganizationId organizationId, SubmissionStatus status) {
        Instant now = Instant.now();
        boolean started = status != SubmissionStatus.DRAFT;
        boolean decided = status == SubmissionStatus.APPROVED || status == SubmissionStatus.REJECTED;

        jdbc.update(
                """
                INSERT INTO opsflow.submission (id, organization_id, reporting_period_id, status,
                                                reviewer_id, submitted_at, submitted_by,
                                                decided_at, decided_by, rejection_reason, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                organizationId.value(),
                period.value(),
                status.name(),
                status == SubmissionStatus.UNDER_REVIEW ? reviewerId.value() : null,
                started ? Timestamp.from(now) : null,
                started ? reporterId.value() : null,
                decided ? Timestamp.from(now) : null,
                decided ? reviewerId.value() : null,
                status == SubmissionStatus.REJECTED ? "Check the payroll export" : null);
    }

    private UserId provision(Jwt jwt) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").with(as(jwt)))
                .andExpect(status().isOk())
                .andReturn();
        return UserId.of(UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asString()));
    }

    private PeriodId openPeriod(MetricId metric, Set<OrganizationId> participants) {
        var created = periodCommands.create(
                new PeriodCode("2026-Q1"),
                PeriodType.QUARTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        long version = periodCommands
                .replaceMetricScope(
                        created.id(),
                        List.of(new PeriodCommandService.MetricScopeEntry(metric, false, 0)),
                        created.version())
                .version();
        version = periodCommands
                .replaceParticipants(created.id(), new LinkedHashSet<>(participants), version)
                .version();
        periodCommands.open(created.id(), version);
        return created.id();
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

    private static Jwt reporter() {
        return token("reporter-subject", "ria", List.of("USER"));
    }

    private static Jwt reviewer() {
        return token("reviewer-subject", "rex", List.of("REVIEWER"));
    }

    private static Jwt stranger() {
        return token("stranger-subject", "sam", List.of("USER"));
    }
}
