package com.joblens.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.common.exception.ErrorCode;
import com.joblens.api.common.response.ApiError;
import com.joblens.api.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders a 403 in the same {@link ApiError} shape as everything else.
 *
 * <p>403 means the caller is authenticated but not allowed. The message says
 * exactly that and nothing about what would have been required -- describing the
 * missing privilege tells someone what to go after.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ApiError body = ApiError.of(ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED.defaultMessage(),
                request.getRequestURI(),
                CorrelationIdFilter.currentCorrelationId());

        response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
