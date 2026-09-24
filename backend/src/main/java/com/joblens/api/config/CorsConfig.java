package com.joblens.api.config;

import com.joblens.api.common.web.ApiRoutes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Lets the React frontend call the API from its own origin.
 *
 * <p>Expressed as a {@link CorsConfigurationSource} bean rather than through
 * {@code WebMvcConfigurer#addCorsMappings}. Once Spring Security is in the
 * picture it runs its own filter chain, and a request it rejects never reaches
 * Spring MVC -- so MVC-level CORS settings would not be applied to a 401 and the
 * browser would report an opaque CORS failure instead of showing the client a
 * readable error. A single source bean is used by both.
 *
 * <p>Origins are always an explicit allowlist supplied per environment. There is
 * no wildcard and no {@code allowCredentials}: wildcards are how a public API
 * ends up callable from any page on the internet, and credentials are not needed
 * because authentication uses a bearer token in the {@code Authorization}
 * header rather than a cookie.
 *
 * <p>Only the versioned API is exposed. Actuator is deliberately left out, so a
 * browser on another origin cannot reach the management endpoints at all.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());

        // The browser needs to be allowed to send Authorization and
        // Content-Type; listing "*" covers those without enumerating headers we
        // would then have to keep in sync.
        configuration.setAllowedHeaders(List.of("*"));

        // Without this the frontend cannot read the correlation id off a
        // response, which is the whole point of returning it.
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));

        configuration.setMaxAge(corsProperties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiRoutes.API_V1 + "/**", configuration);
        return source;
    }
}
