/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import com.opsflow.platform.error.ProblemDetailFactory;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless bearer-token security.
 *
 * <p>Every endpoint is closed unless explicitly opened: the chain ends in {@code denyAll}, so a
 * new controller is unreachable until someone deliberately decides who may reach it. The reverse
 * default — open unless secured — means a forgotten rule is a vulnerability rather than a 403.
 *
 * @see <a href="file:../../../../../../../../docs/adr/0003-identity-and-authorization.md">ADR-0003</a>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemDetailFactory problems, ObjectMapper objectMapper) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                        // Probes must answer before and during authentication problems:
                        // gating them on the very system that might be failing is how a
                        // dependency outage becomes a restart loop.
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Everything else on actuator is closed at the security layer as well
                        // as by management endpoint exposure. Two independent gates, because
                        // /actuator/env leaking configuration is a serious enough outcome.
                        .requestMatchers("/actuator/**")
                        .denyAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        // Nothing else is reachable. A new endpoint is denied until a rule
                        // above deliberately admits it.
                        .anyRequest()
                        .denyAll())

                // No server-side session and no JSESSIONID: the token is the whole state.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF exists because browsers attach cookies automatically. This API
                // authenticates only via an Authorization header that JavaScript must set
                // deliberately, and issues no session cookie, so a cross-site form post
                // carries no credentials and is simply unauthenticated.
                //
                // TRIPWIRE: if any authentication ever moves to cookies — a BFF, "remember
                // me", anything — CSRF protection must be re-enabled in the same commit.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        // This is a JSON API: it serves no scripts, styles or frames, so the
                        // most restrictive policy possible is also a correct one.
                        .contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(
                                new ProblemAuthenticationEntryPoint(problems, objectMapper))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                                new ProblemAuthenticationEntryPoint(problems, objectMapper))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(problems, objectMapper)));

        return http.build();
    }

    /**
     * Validates signature, issuer, expiry and not-before (with a small clock-skew tolerance),
     * and additionally that the token was issued for this API.
     *
     * <p>{@code withIssuerLocation} resolves the provider's metadata lazily, on first use, rather
     * than during startup. That matters twice: the application starts and reports healthy when
     * the identity provider is briefly unreachable instead of failing to boot, and the test suite
     * does not silently require a running Keycloak to load an application context.
     */
    @Bean
    public NimbusJwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        String issuerUri = properties.getJwt().getIssuerUri();
        String jwkSetUri = properties.getJwt().getJwkSetUri();

        NimbusJwtDecoder decoder = jwkSetUri != null && !jwkSetUri.isBlank()
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), new AudienceValidator(securityProperties.audience()));
        decoder.setJwtValidator(validators);
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Exact origins from configuration. Never a wildcard, and never a reflected
        // Origin header: with credentials allowed that combination is the classic
        // CORS foot-gun.
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location", "X-Correlation-Id"));
        // Bearer tokens, not cookies: the browser never needs to attach credentials.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
