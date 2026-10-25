package com.rewabank.accounts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Defense-in-depth layer for internal debit/credit endpoints.
 * Rejects requests that don't carry the correct X-Internal-Call secret,
 * independent of Istio AuthorizationPolicy enforcement.
 */
public class InternalCallFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Call";
    private static final Pattern INTERNAL_PATH =
            Pattern.compile(".*/api/v1/accounts/[^/]+/(debit|credit)$");

    private final String expectedSecret;

    public InternalCallFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (INTERNAL_PATH.matcher(request.getRequestURI()).matches()) {
            String header = request.getHeader(HEADER_NAME);
            if (!expectedSecret.equals(header)) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}