/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The Angular client is generated from this document and CI fails on drift, so it is a contract. */
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
