package com.joblens.api.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports what this deployment is, so a client can confirm which build it is
 * talking to. Deliberately trivial and deliberately public.
 *
 * <p>Operational health belongs to Actuator's {@code /actuator/health}; this
 * endpoint exists so the frontend never has to depend on management endpoints
 * and we never have to expose internal health detail to a browser.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/meta")
public class MetaController {

    private final String applicationName;
    private final String version;
    private final String environment;

    public MetaController(@Value("${spring.application.name}") String applicationName,
                          @Value("${joblens.version}") String version,
                          @Value("${joblens.environment}") String environment) {
        this.applicationName = applicationName;
        this.version = version;
        this.environment = environment;
    }

    @GetMapping
    public MetaResponse meta() {
        return new MetaResponse(applicationName, version, environment);
    }

    public record MetaResponse(String application, String version, String environment) {
    }
}
