package com.vishwas.ticketing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ticketing System API")
                .version("v1")
                .description("Phase 0 foundation. Business endpoints arrive from Phase 1."));
    }
}