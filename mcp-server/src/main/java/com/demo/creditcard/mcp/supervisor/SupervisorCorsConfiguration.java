package com.demo.creditcard.mcp.supervisor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SupervisorCorsConfiguration implements WebMvcConfigurer {

    private static final String AGENT_SERVER_ORIGIN = "http://localhost:8080";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/supervisor/**")
                .allowedOrigins(AGENT_SERVER_ORIGIN)
                .allowedMethods("GET", "POST");
    }
}
