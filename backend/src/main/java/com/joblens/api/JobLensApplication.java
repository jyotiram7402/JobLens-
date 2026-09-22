package com.joblens.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JobLens backend.
 *
 * <p>The application is a modular monolith: one deployable unit, with domain
 * boundaries expressed as packages under {@code com.joblens.api}. Component,
 * entity and repository scanning all start from this package, so a new module
 * is picked up simply by existing.
 *
 * @see com.joblens.api.common shared kernel and its dependency rules
 */
@SpringBootApplication
public class JobLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobLensApplication.class, args);
    }
}
