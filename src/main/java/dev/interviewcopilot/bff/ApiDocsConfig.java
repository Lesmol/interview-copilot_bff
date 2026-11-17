package dev.interviewcopilot.bff;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ApiDocsConfig {

    /** Contract version. It matches the {@code /api/v1} path: a breaking change adds {@code /api/v2}. */
    static final String API_VERSION = "1.0.0";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("Interview Co-Pilot BFF API").version(API_VERSION));
    }
}
