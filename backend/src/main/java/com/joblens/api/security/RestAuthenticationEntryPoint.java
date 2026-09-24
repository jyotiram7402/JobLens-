package com.joblens.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;
import com.joblens.api.common.response.ApiError;
import com.joblens.api.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders a 401 in the same {@link ApiError} shape as everything else.
 *
 * <p>Spring Security rejects unauthenticated requests inside the filter chain,
 * before any controller or {@code @RestControllerAdvice} is reached, so the
 * global exception handler never sees them. Without this class the API would
 * have two different error formats -- one for business failures and Spring
 * Security's own for authentication -- and every client would have to parse both.
 *
 * <p>If {@link JwtAuthenticationFilter} recorded why a token was rejected, that
 * reason is used, so the client learns whether to refresh or to log in again.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;

        Object failure = request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE);
        if (failure instanceof ApplicationException applicationException) {
            errorCode = applicationException.errorCode();
        }

        ApiError body = ApiError.of(errorCode, errorCode.defaultMessage(),
                request.getRequestURI(), CorrelationIdFilter.currentCorrelationId());

        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
