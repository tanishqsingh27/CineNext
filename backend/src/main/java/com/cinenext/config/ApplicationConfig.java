package com.cinenext.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    @Bean
    Clock clock() { return Clock.system(ZoneId.of("Asia/Kolkata")); }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("CineNext booking API").version("0.2.0")
                .description("Movie discovery, customer authentication, transactional seat holds, simulated payments, QR tickets and role-protected cinema administration."))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",
                        new io.swagger.v3.oas.models.security.SecurityScheme().type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
