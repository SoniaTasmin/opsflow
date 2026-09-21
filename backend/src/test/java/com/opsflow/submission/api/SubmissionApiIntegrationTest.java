/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.opsflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
class SubmissionApiIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";
    private static final String SUBMISSIONS = "/api/v1/submissions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private UUID alpha;
    private UUID beta;
    private UUID headcount;
    private UUID revenue;
    private UUID period;
    private UUID authorId;

    @BeforeEach
    void setUp() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Unverifiable token"));
        jdbc.execute(
                """
                TRUNCATE opsflow.submission_transition, opsflow.submission_value, opsflow.submission,
                         opsflow.reporting_period_metric, opsflow.reporting_period_organization,
                         opsflow.reporting_period, opsflow.metric_definition,
                         opsflow.org_membership, opsflow.organization, opsflow.app_user CASCADE
                """);

        alpha = organization("ALPHA");
        beta = organization("BETA");
        headcount = metric("HEADCOUNT", ValueType.INTEGER, new NumericRange(BigDecimal.ZERO, new BigDecimal("10000")));
        revenue = metric("REVENUE", ValueType.DECIMAL, NumericRange.UNBOUNDED);
        period = openPeriod("2026-Q1", List.of(alpha, beta), Map.of(headcount, true, revenue, false));

        authorId = provision(author());
        UUID otherMemberId = provision(otherMember());
        provision(admin());
        provision(reviewer());
        provision(secondReviewer());

        addMember(alpha, authorId);
        addMember(beta, otherMemberId);
    }

    // ------------------------------------------------------------ authentication and role gates

    @Test
    void everySubmissionEndpointRequiresAuthentication() throws Exception {
        UUID someId = UUID.randomUUID();

        mockMvc.perform(get(SUBMISSIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SUBMISSIONS + "/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SUBMISSIONS + "/" + someId + "/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBMISSIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBMISSIONS + "/" + someId + "/submit")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBMISSIONS + "/" + someId + "/approve")).andExpect(status().isUnauthorized());
    }

    @Test
    void aReviewerCannotAuthorForAnOrganizationTheyDoNotBelongTo() throws Exception {
        mockMvc.perform(post(SUBMISSIONS)
                        .with(as(reviewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(alpha, period)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aReporterCannotReview() throws Exception {
        UUID submission = submitted();

        mockMvc.perform(command(submission, "claim", author(), 1)).andExpect(status().isForbidden());
        mockMvc.perform(command(submission, "approve", author(), 1)).andExpect(status().isForbidden());
    }

    /**
     * Cross-organization access is a 404 rather than a 403: a 403 would confirm the identifier
     * names a real submission. The review commands are not listed because a plain member cannot
     * reach them at all -- the role gate refuses them first, which is a different, earlier answer.
     */
    @Test
    void aMemberOfAnotherOrganizationSeesNothingAtAll() throws Exception {
        UUID submission = draft(alpha);

        mockMvc.perform(get(SUBMISSIONS + "/" + submission).with(as(otherMember())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(SUBMISSIONS + "/" + submission + "/history").with(as(otherMember())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(otherMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(command(submission, "submit", otherMember(), 0)).andExpect(status().isNotFound());
        mockMvc.perform(command(submission, "withdraw", otherMember(), 0)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ the loop

    @Test
    void anAuthorDraftsValuesAndSendsThemForReview() throws Exception {
        UUID submission = draft(alpha);

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.length()").value(1))
                .andExpect(jsonPath("$.editable").value(true));

        mockMvc.perform(command(submission, "submit", author(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void theWholeApprovalLoopWorksAndIsRecorded() throws Exception {
        UUID submission = submitted();

        mockMvc.perform(command(submission, "claim", reviewer(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerName").value("rex Person"));

        mockMvc.perform(post(SUBMISSIONS + "/" + submission + "/reject")
                        .with(as(reviewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Headcount does not match payroll\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Headcount does not match payroll"))
                .andExpect(jsonPath("$.editable").value(true));

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(4, headcount, "44")))
                .andExpect(status().isOk());

        mockMvc.perform(command(submission, "submit", author(), 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        mockMvc.perform(command(submission, "claim", reviewer(), 6)).andExpect(status().isOk());
        mockMvc.perform(command(submission, "approve", reviewer(), 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedByName").value("rex Person"));

        JsonNode history = json(
                mockMvc.perform(get(SUBMISSIONS + "/" + submission + "/history").with(as(author())))
                        .andExpect(status().isOk())
                        .andReturn());

        assertThat(history).hasSize(7);
        assertThat(history.get(0).get("fromStatus").isNull()).isTrue();
        assertThat(statuses(history))
                .containsExactly(
                        "DRAFT", "SUBMITTED", "UNDER_REVIEW", "REJECTED", "SUBMITTED", "UNDER_REVIEW", "APPROVED");
        assertThat(history.get(3).get("reason").asString()).isEqualTo("Headcount does not match payroll");
        assertThat(history.get(3).get("actorName").asString()).isEqualTo("rex Person");
    }

    @Test
    void aReviewerCanHandTheSubmissionBackToTheQueue() throws Exception {
        UUID submission = submitted();
        mockMvc.perform(command(submission, "claim", reviewer(), 2)).andExpect(status().isOk());

        mockMvc.perform(command(submission, "release", reviewer(), 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.reviewerName").doesNotExist());
    }

    @Test
    void anAuthorCanPullBackASubmissionNobodyHasClaimed() throws Exception {
        UUID submission = submitted();

        mockMvc.perform(command(submission, "withdraw", author(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void aReviewIsHeldByWhoeverClaimedIt() throws Exception {
        UUID submission = submitted();
        mockMvc.perform(command(submission, "claim", reviewer(), 2)).andExpect(status().isOk());

        mockMvc.perform(command(submission, "approve", secondReviewer(), 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------ rules

    @Test
    void refusesToSubmitWhileARequiredMetricHasNoValue() throws Exception {
        UUID submission = draft(alpha);
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, revenue, "12.5")))
                .andExpect(status().isOk());

        mockMvc.perform(command(submission, "submit", author(), 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUIRED_METRICS_MISSING"));
    }

    @Test
    void refusesAValueOutsideTheMetricsRange() throws Exception {
        UUID submission = draft(alpha);

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "10001")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALUE_OUT_OF_RANGE"));
    }

    @Test
    void refusesAMetricThePeriodDoesNotCollect() throws Exception {
        UUID unscoped = metric("ABSENCE", ValueType.DECIMAL, NumericRange.UNBOUNDED);
        UUID submission = draft(alpha);

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, unscoped, "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("METRIC_NOT_IN_SCOPE"));
    }

    @Test
    void refusesToChangeAnApprovedSubmission() throws Exception {
        UUID submission = approved();

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(4, headcount, "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_EDITABLE"));
    }

    @Test
    void refusesToApproveSomethingNobodyHasReviewed() throws Exception {
        UUID submission = draft(alpha);

        mockMvc.perform(command(submission, "approve", reviewer(), 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void refusesAnApprovalByThePersonWhoSubmitted() throws Exception {
        // The administrator is the only role that can both author and review, which is exactly
        // why the rule exists.
        UUID submission = draftFor(admin(), beta);
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "7")))
                .andExpect(status().isOk());
        mockMvc.perform(command(submission, "submit", admin(), 1)).andExpect(status().isOk());
        mockMvc.perform(command(submission, "claim", admin(), 2)).andExpect(status().isOk());

        mockMvc.perform(command(submission, "approve", admin(), 3))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    void refusesASecondSubmissionForTheSameOrganizationAndPeriod() throws Exception {
        draft(alpha);

        mockMvc.perform(post(SUBMISSIONS)
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(alpha, period)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void refusesAnOrganizationThatDoesNotReportInThePeriod() throws Exception {
        UUID gamma = organization("GAMMA");
        addMember(gamma, authorId);

        mockMvc.perform(post(SUBMISSIONS)
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(gamma, period)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_IN_PERIOD_SCOPE"));
    }

    @Test
    void refusesWorkOnAPeriodThatIsNotOpen() throws Exception {
        UUID draftPeriod = draftPeriod("2026-Q3");

        mockMvc.perform(post(SUBMISSIONS)
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(alpha, draftPeriod)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_NOT_OPEN"));
    }

    @Test
    void refusesAStaleVersion() throws Exception {
        UUID submission = draft(alpha);
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "42")))
                .andExpect(status().isOk());

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "99")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(get(SUBMISSIONS + "/" + submission).with(as(author())))
                .andExpect(jsonPath("$.values[0].value").value(42));
    }

    // ------------------------------------------------------------ listing

    @Test
    void theListShowsOnlyTheCallersOrganizations() throws Exception {
        draft(alpha);
        draftFor(otherMember(), beta);

        mockMvc.perform(get(SUBMISSIONS).with(as(author())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationCode").value("ALPHA"));

        mockMvc.perform(get(SUBMISSIONS).with(as(reviewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void theReviewQueueIsTheListFilteredByStatus() throws Exception {
        submitted();
        draftFor(otherMember(), beta);

        mockMvc.perform(get(SUBMISSIONS).param("status", "SUBMITTED").with(as(reviewer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));
    }

    @Test
    void aUserWithNoMembershipsSeesAnEmptyListRatherThanEverything() throws Exception {
        draft(alpha);
        Jwt stranger = token("stranger-subject", "sam", List.of("USER"));
        provision(stranger);

        mockMvc.perform(get(SUBMISSIONS).with(as(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void editingValuesAddsNoHistory() throws Exception {
        UUID submission = draft(alpha);
        int afterCreation = historyOf(submission).size();

        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "42")))
                .andExpect(status().isOk());
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(1, headcount, "43")))
                .andExpect(status().isOk());

        assertThat(historyOf(submission)).hasSize(afterCreation);
    }

    @Test
    void eachRealTransitionAddsExactlyOneHistoryEntry() throws Exception {
        UUID submission = submitted();
        assertThat(historyOf(submission)).hasSize(2);

        mockMvc.perform(command(submission, "claim", reviewer(), 2)).andExpect(status().isOk());

        assertThat(historyOf(submission)).hasSize(3);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM opsflow.submission_transition WHERE submission_id = ?",
                        Long.class,
                        submission))
                .isEqualTo(3L);
    }

    /**
     * Scoping belongs in the query, not after it. Were it applied to a fetched page, this caller
     * would receive an empty first page while the total still claimed a result.
     */
    @Test
    void theListIsScopedBeforeItIsPaginated() throws Exception {
        draft(alpha);
        draftFor(otherMember(), beta);

        mockMvc.perform(get(SUBMISSIONS).param("page", "0").param("size", "1").with(as(author())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].organizationCode").value("ALPHA"));
    }

    private JsonNode historyOf(UUID submission) throws Exception {
        return json(
                mockMvc.perform(get(SUBMISSIONS + "/" + submission + "/history").with(as(author())))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    // ------------------------------------------------------------ helpers

    private UUID draft(UUID organizationId) throws Exception {
        return draftFor(author(), organizationId);
    }

    private UUID draftFor(Jwt actor, UUID organizationId) throws Exception {
        MvcResult result = mockMvc.perform(post(SUBMISSIONS)
                        .with(as(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(organizationId, period)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    private UUID submitted() throws Exception {
        UUID submission = draft(alpha);
        mockMvc.perform(put(SUBMISSIONS + "/" + submission + "/values")
                        .with(as(author()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valuesBody(0, headcount, "42")))
                .andExpect(status().isOk());
        mockMvc.perform(command(submission, "submit", author(), 1)).andExpect(status().isOk());
        return submission;
    }

    private UUID approved() throws Exception {
        UUID submission = submitted();
        mockMvc.perform(command(submission, "claim", reviewer(), 2)).andExpect(status().isOk());
        mockMvc.perform(command(submission, "approve", reviewer(), 3)).andExpect(status().isOk());
        return submission;
    }

    private org.springframework.test.web.servlet.RequestBuilder command(
            UUID submission, String command, Jwt actor, long version) {
        return post(SUBMISSIONS + "/" + submission + "/" + command)
                .with(as(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}");
    }

    private static String createBody(UUID organizationId, UUID reportingPeriodId) {
        return "{\"organizationId\":\"" + organizationId + "\",\"reportingPeriodId\":\"" + reportingPeriodId + "\"}";
    }

    private static String valuesBody(long version, UUID metricId, String value) {
        return "{\"values\":[{\"metricId\":\"" + metricId + "\",\"value\":" + value + "}],\"version\":" + version + "}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> statuses(JsonNode history) {
        return history.valueStream()
                .map(entry -> entry.get("toStatus").asString())
                .toList();
    }

    private UUID provision(Jwt jwt) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").with(as(jwt)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    private void addMember(UUID organizationId, UUID userId) {
        organizationCommands.addMember(OrganizationId.of(organizationId), UserId.of(userId));
    }

    private UUID organization(String code) {
        return organizationCommands
                .create(new OrganizationCode(code), code + " unit")
                .id()
                .value();
    }

    private UUID metric(String code, ValueType valueType, NumericRange range) {
        return metricCommands
                .create(new MetricCode(code), code + " metric", null, "unit", valueType, range)
                .id()
                .value();
    }

    private UUID draftPeriod(String code) {
        return periodCommands
                .create(
                        new PeriodCode(code),
                        PeriodType.QUARTER,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 10, 15))
                .id()
                .value();
    }

    private UUID openPeriod(String code, List<UUID> organizations, Map<UUID, Boolean> metrics) {
        var created = periodCommands.create(
                new PeriodCode(code),
                PeriodType.QUARTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        PeriodId id = created.id();

        List<PeriodCommandService.MetricScopeEntry> scope = metrics.entrySet().stream()
                .map(entry ->
                        new PeriodCommandService.MetricScopeEntry(MetricId.of(entry.getKey()), entry.getValue(), 0))
                .toList();
        long version =
                periodCommands.replaceMetricScope(id, scope, created.version()).version();
        version = periodCommands
                .replaceParticipants(
                        id,
                        organizations.stream()
                                .map(OrganizationId::of)
                                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                        version)
                .version();
        return periodCommands.open(id, version).id().value();
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

    private static Jwt author() {
        return token("author-subject", "ria", List.of("USER"));
    }

    private static Jwt otherMember() {
        return token("other-subject", "bob", List.of("USER"));
    }

    private static Jwt reviewer() {
        return token("reviewer-subject", "rex", List.of("REVIEWER"));
    }

    private static Jwt secondReviewer() {
        return token("second-reviewer-subject", "rita", List.of("REVIEWER"));
    }
}
