package com.joblens.api.common.web;

/**
 * Base paths for the HTTP API.
 *
 * <p>Every endpoint is versioned from the start. Controllers build on these
 * constants instead of repeating the prefix, so the version appears in exactly
 * one place and cannot drift between modules.
 */
public final class ApiRoutes {

    /** Prefix for all versioned API endpoints. */
    public static final String API_V1 = "/api/v1";

    private ApiRoutes() {
    }
}
