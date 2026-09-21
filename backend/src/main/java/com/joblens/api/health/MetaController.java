package com.joblens.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal public endpoint used by the frontend to confirm it is pointed at a
 * reachable JobLens API. Operational health lives under /actuator/health.
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final String applicationName;
    private final String version;

    public MetaController(@Value("${spring.application.name}") String applicationName,
                          @Value("${joblens.version}") String version) {
        this.applicationName = applicationName;
        this.version = version;
    }

    @GetMapping
    public MetaResponse meta() {
        return new MetaResponse(applicationName, version);
    }

    public record MetaResponse(String application, String version) {
    }
}
