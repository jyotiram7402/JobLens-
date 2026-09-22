package com.joblens.api.config;

import com.joblens.api.common.web.ApiRoutes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the React frontend call the API from its own origin.
 *
 * <p>Origins are always an explicit allowlist supplied per environment. There is
 * no wildcard and no {@code allowCredentials}: wildcards are how a public API
 * ends up callable from any page on the internet, and credentials are not needed
 * because authentication will use a bearer token in the Authorization header
 * rather than a cookie.
 *
 * <p>Only the versioned API is exposed. Actuator is deliberately left out, so a
 * browser on another origin cannot reach the management endpoints at all.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ApiRoutes.API_V1 + "/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
                .allowedHeaders("*")
                .exposedHeaders("X-Correlation-Id")
                .maxAge(corsProperties.maxAgeSeconds());
    }
}
