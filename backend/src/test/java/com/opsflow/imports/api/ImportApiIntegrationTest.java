/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.opsflow.support.ImportTestConfig;
import com.opsflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
@Import(ImportTestConfig.class)
// Small enough to prove the row cap without building a file nobody wants to read in a diff.
@TestPropertySource(properties = "opsflow.imports.max-rows=10")
class ImportApiIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";
    private static final String IMPORTS = "/api/v1/imports";
    private static final String HEADER = "organization_code,metric_code,value,note\n";
    private static final String CSV = HEADER + "ALPHA,HEADCOUNT,42,\n";

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
    private OrganizationId outsider;
    private PeriodId period;
    private PeriodId secondPeriod;

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
        outsider = organizationCommands
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
        period = openPeriod("2026-Q1", LocalDate.of(2026, 1, 1), headcount, Set.of(alpha));
        secondPeriod = openPeriod("2026-Q2", LocalDate.of(2026, 4, 1), headcount, Set.of(alpha));

        UserId reporter = provision(reporter());
        provision(admin());
        provision(stranger());
        organizationCommands.addMember(alpha, reporter);
    }

    @Test
    void everyImportEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get(IMPORTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(IMPORTS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(IMPORTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void aReviewerCannotStartAnImport() throws Exception {
        mockMvc.perform(upload(CSV, reviewer(), period, null, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserOutsideTheOrganizationCannotImportForIt() throws Exception {
        mockMvc.perform(upload(CSV, stranger(), period, alpha, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMemberIsAdmittedAsAMember() throws Exception {
        UUID job = start(CSV, reporter(), period, alpha);

        assertThat(authorizationModeOf(job)).isEqualTo("ORGANIZATION_MEMBER");
    }

    @Test
    void anAdministratorIsAdmittedAsAnOverride() throws Exception {
        UUID job = start(CSV, admin(), period, alpha);

        assertThat(authorizationModeOf(job)).isEqualTo("ADMIN_OVERRIDE");
    }

    /** The basis comes from the token's roles; nothing in the request can ask for one. */
    @Test
    void aReporterCannotAskForAnAdminOverride() throws Exception {
        UUID job = start(CSV, reporter(), period, null);

        assertThat(authorizationModeOf(job)).isEqualTo("ORGANIZATION_MEMBER");
    }

    @Test
    void theSameKeyAndRequestReturnsTheJobThatAlreadyExists() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID first = UUID.fromString(json(mockMvc.perform(upload(CSV, reporter(), period, alpha, key))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString());

        MvcResult replay = mockMvc.perform(upload(CSV, reporter(), period, alpha, key))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(UUID.fromString(json(replay).get("id").asString())).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.import_job", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void theSameKeyWithADifferentPeriodIsAConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(upload(CSV, reporter(), period, alpha, key)).andExpect(status().isCreated());

        mockMvc.perform(upload(CSV, reporter(), secondPeriod, alpha, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void theSameKeyWithADifferentFileIsAConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(upload(CSV, reporter(), period, alpha, key)).andExpect(status().isCreated());

        mockMvc.perform(upload(HEADER + "ALPHA,HEADCOUNT,43,\n", reporter(), period, alpha, key))
                .andExpect(status().isConflict());
    }

    @Test
    void theSameKeyWithADifferentScopeIsAConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(upload(CSV, reporter(), period, alpha, key)).andExpect(status().isCreated());

        mockMvc.perform(upload(CSV, reporter(), period, null, key)).andExpect(status().isConflict());
    }

    /** The retry-after-failure path: identical content, a new key, a second import really runs. */
    @Test
    void aNewKeyWithTheSameFileStartsASecondImport() throws Exception {
        UUID first = start(CSV, reporter(), period, alpha);
        UUID second = start(CSV, reporter(), period, alpha);

        assertThat(second).isNotEqualTo(first);
    }

    /** A missing required header makes the request incomplete, not unprocessable. */
    @Test
    void anImportWithoutAnIdempotencyKeyIsRefusedAsABadRequest() throws Exception {
        mockMvc.perform(upload(CSV, reporter(), period, alpha, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void anImportWithABlankIdempotencyKeyIsRefusedTheSameWay() throws Exception {
        mockMvc.perform(upload(CSV, reporter(), period, alpha, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void aFileThatIsNotCsvIsRefused() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "values.xlsx", "application/vnd.ms-excel", CSV.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(IMPORTS)
                        .file(file)
                        .param("reportingPeriodId", period.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(as(reporter())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMPORT_FORMAT"));
    }

    @Test
    void anOrganizationOutsideThePeriodIsRefused() throws Exception {
        organizationCommands.addMember(outsider, provision(reporter()));

        mockMvc.perform(upload(
                        CSV, reporter(), period, outsider, UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_IN_PERIOD_SCOPE"));
    }

    @Test
    void aFileWithMoreRowsThanAllowedIsRefusedAndNotKept() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int row = 0; row < 12; row++) {
            csv.append("ALPHA,HEADCOUNT,1,\n");
        }

        mockMvc.perform(upload(
                        csv.toString(),
                        reporter(),
                        period,
                        alpha,
                        UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROW_LIMIT_EXCEEDED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.import_job", Long.class))
                .isZero();
    }

    @Test
    void theListShowsOnlyImportsTheCallerMaySee() throws Exception {
        start(CSV, reporter(), period, alpha);

        mockMvc.perform(get(IMPORTS).with(as(reporter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(IMPORTS).with(as(stranger())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(IMPORTS).with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anImportSomeoneElseStartedIsInvisibleRatherThanForbidden() throws Exception {
        UUID job = start(CSV, reporter(), period, alpha);

        mockMvc.perform(get(IMPORTS + "/" + job).with(as(stranger()))).andExpect(status().isNotFound());
        mockMvc.perform(get(IMPORTS + "/" + job + "/errors").with(as(stranger())))
                .andExpect(status().isNotFound());
    }

    @Test
    void scopingIsAppliedBeforePagination() throws Exception {
        start(CSV, reporter(), period, alpha);
        // A page of one would be empty if the caller's rows were filtered after the query.
        mockMvc.perform(get(IMPORTS).param("page", "0").param("size", "1").with(as(reporter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void theJobReportsWhatItAppliedOnceItHasFinished() throws Exception {
        UUID job = start(CSV, reporter(), period, alpha);

        awaitTerminal(job);

        mockMvc.perform(get(IMPORTS + "/" + job).with(as(reporter())))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.submittedRows").value(1));
    }

    private UUID start(String csv, Jwt actor, PeriodId reportingPeriodId, OrganizationId organizationId)
            throws Exception {
        MvcResult result = mockMvc.perform(upload(
                        csv,
                        actor,
                        reportingPeriodId,
                        organizationId,
                        UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json(result).get("id").asString());
        awaitTerminal(id);
        return id;
    }

    private void awaitTerminal(UUID job) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            String status =
                    jdbc.queryForObject("SELECT status FROM opsflow.import_job WHERE id = ?", String.class, job);
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        });
    }

    private MockMultipartHttpServletRequestBuilder upload(
            String csv, Jwt actor, PeriodId reportingPeriodId, OrganizationId organizationId, String key) {
        MockMultipartFile file =
                new MockMultipartFile("file", "values.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder request =
                multipart(IMPORTS).file(file).param("reportingPeriodId", reportingPeriodId.toString());
        if (organizationId != null) {
            request.param("organizationId", organizationId.toString());
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return (MockMultipartHttpServletRequestBuilder) request.with(as(actor));
    }

    private String authorizationModeOf(UUID job) {
        return jdbc.queryForObject("SELECT authorization_mode FROM opsflow.import_job WHERE id = ?", String.class, job);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UserId provision(Jwt jwt) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").with(as(jwt)))
                .andExpect(status().isOk())
                .andReturn();
        return UserId.of(UUID.fromString(json(result).get("id").asString()));
    }

    private PeriodId openPeriod(String code, LocalDate start, MetricId metric, Set<OrganizationId> participants) {
        var created = periodCommands.create(
                new PeriodCode(code), PeriodType.QUARTER, start, start.plusMonths(2), start.plusMonths(3));
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
