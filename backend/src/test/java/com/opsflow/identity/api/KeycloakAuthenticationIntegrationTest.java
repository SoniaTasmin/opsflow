/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.support.PostgresContainerConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The one test that exercises authentication against a real Keycloak.
 *
 * <p>Everything else uses {@code jwt()} post-processors, which are orders of magnitude faster but
 * bypass token validation entirely. This test is what proves the parts they cannot: that the
 * committed realm actually imports, that its audience mapper puts {@code opsflow-api} in the
 * token, that a genuinely signed token passes signature and issuer validation, that realm roles
 * arrive as application roles, and that a first-time login provisions a local user.
 *
 * <p>It runs against the same {@code realm-opsflow.json} the local stack uses, so a change that
 * breaks the realm breaks the build rather than someone's morning.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfig.class)
@ActiveProfiles("test")
@Testcontainers
class KeycloakAuthenticationIntegrationTest {

    private static final String REALM = "opsflow";
    private static final DockerImageName KEYCLOAK_IMAGE = DockerImageName.parse("quay.io/keycloak/keycloak:26.7");

    @SuppressWarnings("resource")
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(KEYCLOAK_IMAGE)
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../deploy/keycloak/realm-opsflow.json"),
                    "/opt/keycloak/data/import/realm-opsflow.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    private static String issuerUri() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/" + REALM;
    }

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                KeycloakAuthenticationIntegrationTest::issuerUri);
        // Must be overridden too: application.yml sets a default pointing at the local
        // Compose stack, and leaving it would have this test verifying signatures against
        // whatever Keycloak happened to be running on the developer's machine.
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuerUri() + "/protocol/openid-connect/certs");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    /** Obtains a genuine token the way a developer would, via the local-only dev client. */
    private String tokenFor(String username) throws Exception {
        return tokenFor(username, "opsflow-dev-cli");
    }

    private String tokenFor(String username, String clientId) throws Exception {
        String form = "client_id=" + clientId + "&grant_type=password&username=" + username + "&password=password";
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(issuerUri() + "/protocol/openid-connect/token"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("token endpoint").isEqualTo(200);
        return objectMapper.readTree(response.body()).get("access_token").asString();
    }

    private ResponseEntityLike callMe(String bearerToken) {
        RestClient client = RestClient.create("http://localhost:" + port);
        var spec = client.get().uri("/api/v1/me");
        if (bearerToken != null) {
            spec = spec.header("Authorization", "Bearer " + bearerToken);
        }
        var response =
                spec.retrieve().onStatus(status -> true, (req, res) -> {}).toEntity(String.class);
        return new ResponseEntityLike(response.getStatusCode().value(), response.getBody());
    }

    private record ResponseEntityLike(int status, String body) {}

    @Test
    @DisplayName("a genuine Keycloak token authenticates and provisions a local user")
    void aGenuineTokenIsAcceptedEndToEnd() throws Exception {
        ResponseEntityLike response = callMe(tokenFor("reporter"));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("username").asString()).isEqualTo("reporter");
        // Application-owned identifier, not the token's subject.
        assertThat(body.get("id").asString()).isNotEqualTo(subjectOf(tokenFor("reporter")));
        assertThat(body.get("roles").toString()).contains("USER");
    }

    @Test
    @DisplayName("realm roles arrive as application roles")
    void realmRolesBecomeApplicationRoles() throws Exception {
        JsonNode body = objectMapper.readTree(callMe(tokenFor("admin")).body());

        assertThat(body.get("roles").toString()).contains("ADMIN").contains("USER");
    }

    @Test
    @DisplayName("a user with no realm roles is authenticated but entitled to nothing")
    void aUserWithNoRealmRolesGetsNoRoles() throws Exception {
        JsonNode body = objectMapper.readTree(callMe(tokenFor("noroles")).body());

        assertThat(body.get("roles")).isEmpty();
    }

    @Test
    @DisplayName("a request with no token is rejected")
    void anUnauthenticatedRequestIsRejected() {
        assertThat(callMe(null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("a structurally valid but unverifiable token is rejected")
    void aForgedTokenIsRejected() {
        String forged = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhdHRhY2tlciIsImF1ZCI6Im9wc2Zsb3ctYXBpIn0."
                + "c2lnbmF0dXJlLXRoYXQtaXMtbm90LXZhbGlk";

        assertThat(callMe(forged).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("the realm's audience mapper puts this API in the token")
    void theRealmIssuesTokensForThisApi() throws Exception {
        // If the audience mapper is ever removed from the realm, every token silently
        // stops being valid for this API. Asserting it here makes that a build failure.
        JsonNode claims = claimsOf(tokenFor("reporter"));

        assertThat(claims.get("aud").toString()).contains("opsflow-api");
        assertThat(claims.get("iss").asString()).isEqualTo(issuerUri());
    }

    @Test
    @DisplayName("a validly signed token issued for a different client is rejected")
    void aTokenWithoutThisApisAudienceIsRejected() throws Exception {
        // Correctly signed, from the right issuer, not expired - and still not ours.
        // Signature and issuer checks prove a token is genuine, never that it was meant
        // for this API; without the audience check, any token from the realm would work
        // here, including one a user consented to give an entirely different application.
        String otherClientToken = tokenFor("reporter", "opsflow-wrong-audience-cli");
        JsonNode audience = claimsOf(otherClientToken).get("aud");
        assertThat(audience == null ? "" : audience.toString()).doesNotContain("opsflow-api");

        assertThat(callMe(otherClientToken).status()).isEqualTo(401);
    }

    private JsonNode claimsOf(String jwt) {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private String subjectOf(String jwt) {
        return claimsOf(jwt).get("sub").asString();
    }
}
