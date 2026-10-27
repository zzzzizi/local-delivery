package com.localdelivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.exception.ApiExceptionHandler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reset invalidates every existing session, including sessions on a future backend replica. */
public class SessionVersionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public SessionVersionFilter(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal account) {
            try {
                var versions = jdbc.queryForList("SELECT session_version FROM users WHERE id = ?", Long.class, Long.valueOf(account.getUsername()));
                if (versions.isEmpty() || versions.getFirst() != account.sessionVersion()) {
                    SecurityContextHolder.clearContext();
                    var session = request.getSession(false);
                    if (session != null) session.invalidate();
                    ApiError.write(json, response, HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again.");
                    return;
                }
            } catch (org.springframework.dao.DataAccessException failure) {
                ApiError.write(json, response, HttpStatus.SERVICE_UNAVAILABLE, "Database temporarily unavailable. Try again later.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
