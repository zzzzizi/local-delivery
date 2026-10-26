package com.localdelivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.exception.ApiExceptionHandler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
                    error(response, 401, "Unauthorized", "Your session has expired. Please log in again.");
                    return;
                }
            } catch (org.springframework.dao.DataAccessException failure) {
                error(response, 503, "Service Unavailable", "Database temporarily unavailable. Try again later.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private void error(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), new ApiError(OffsetDateTime.now(ZoneOffset.UTC), status, error, message, Map.of()));
    }
}
