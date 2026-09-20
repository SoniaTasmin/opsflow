/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata.
 *
 * <p>The generated document is not just documentation: the Angular client is generated from it,
 * and CI fails when the committed client and the live spec disagree. That makes the spec a
 * build-enforced contract rather than a description that drifts.
 *
 * <p>Security schemes are added with the resource server in M2.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI opsFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpsFlow API")
                        .version("v1")
                        .description(
                                """
                                Reporting, approval and bulk-import platform.

                                All errors share one shape (RFC 9457, media type
                                application/problem+json) and carry a stable "code" plus the
                                request's "correlationId". Clients switch on "code"; "title" and
                                "detail" are human-readable and may be reworded at any time.
                                """)
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
