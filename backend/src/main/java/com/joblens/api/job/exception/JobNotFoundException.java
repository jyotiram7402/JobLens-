package com.joblens.api.job.exception;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;

import java.util.UUID;

/**
 * No job matches the requested id. Rendered as HTTP 404 with the
 * {@code JOB_NOT_FOUND} code.
 */
public class JobNotFoundException extends ApplicationException {

    public JobNotFoundException(UUID id) {
        super(ErrorCode.JOB_NOT_FOUND, "No job found with id " + id);
    }
}
