package com.localdelivery.service;

import com.localdelivery.dto.DeliveryRequestResponse;
import com.localdelivery.dto.JobApplicationResponse;
import com.localdelivery.exception.InvalidStatusTransitionException;
import com.localdelivery.exception.RequestAlreadyAcceptedException;
import com.localdelivery.exception.RequestNotOpenException;
import com.localdelivery.exception.ResourceNotFoundException;
import com.localdelivery.exception.SelfAcceptanceException;
import com.localdelivery.repository.DeliveryRequestRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobApplicationService {
    private static final String DETAILS = """
            SELECT a.*, r.title, r.status::text AS request_status, r.customer_id, u.name
            FROM job_applications a JOIN delivery_requests r ON r.id = a.request_id
            JOIN users u ON u.id = a.helper_id
            """;
    private static final RowMapper<JobApplicationResponse> APPLICATION = (rs, row) -> new JobApplicationResponse(
            rs.getLong("id"), rs.getLong("request_id"), rs.getString("title"), rs.getString("request_status"),
            rs.getLong("customer_id"), rs.getLong("helper_id"), rs.getString("name"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final DeliveryRequestRepository requests;
    private final EntityManager entityManager;
    private final AuthRateLimiter limiter;

    public JobApplicationService(JdbcTemplate jdbc, DeliveryRequestRepository requests,
            EntityManager entityManager, AuthRateLimiter limiter) {
        this.jdbc = jdbc; this.requests = requests; this.entityManager = entityManager; this.limiter = limiter;
    }

    @Transactional
    public JobApplicationResponse apply(long requestId, long actor) {
        var request = loadRequest(requestId, true);
        if (number(request, "customer_id") == actor) throw new SelfAcceptanceException();
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, actor) == 0)
            throw new ResourceNotFoundException("Helper not found.");
        var previous = jdbc.query(DETAILS + " WHERE a.request_id = ? AND a.helper_id = ?", APPLICATION, requestId, actor);
        if (!previous.isEmpty() && (previous.getFirst().status().equals("PENDING") || previous.getFirst().status().equals("APPROVED")))
            return previous.getFirst();
        requireOpen(request);
        if (!previous.isEmpty() && !previous.getFirst().status().equals("WITHDRAWN"))
            throw new InvalidStatusTransitionException("This helper request has already been decided.");
        limiter.jobApplication(actor);
        if (!previous.isEmpty()) {
            long id = previous.getFirst().id();
            jdbc.update("UPDATE job_applications SET status = 'PENDING', updated_at = clock_timestamp() WHERE id = ?", id);
            return loadApplication(requestId, id);
        }
        Long id = jdbc.queryForObject("INSERT INTO job_applications(request_id, helper_id) VALUES (?, ?) RETURNING id", Long.class, requestId, actor);
        return loadApplication(requestId, id);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse.Page list(long requestId, long actor, int page) {
        var request = loadRequest(requestId, false);
        boolean customer = number(request, "customer_id") == actor;
        var rows = jdbc.query(DETAILS + " WHERE a.request_id = ?" + (customer ? "" : " AND a.helper_id = ?")
                + " ORDER BY a.id DESC LIMIT 26 OFFSET ?", APPLICATION,
                customer ? new Object[]{requestId, page * 25L} : new Object[]{requestId, actor, page * 25L});
        return new JobApplicationResponse.Page(rows.stream().limit(25).toList(), rows.size() > 25);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse.Page mine(long actor, int page) {
        var rows = jdbc.query(DETAILS + " WHERE a.helper_id = ? ORDER BY a.id DESC LIMIT 26 OFFSET ?", APPLICATION, actor, page * 25L);
        return new JobApplicationResponse.Page(rows.stream().limit(25).toList(), rows.size() > 25);
    }

    @Transactional
    public DeliveryRequestResponse approve(long requestId, long applicationId, long actor) {
        var request = loadRequest(requestId, true);
        requireCustomer(request, actor);
        var application = loadApplication(requestId, applicationId);
        if (application.status().equals("APPROVED") && number(request, "helper_id") == application.helperId())
            return requestResponse(requestId);
        requireOpen(request);
        requirePending(application);
        // This row lock is shared with apply/decline/withdraw/chat. Increment @Version so an
        // older JPA status update cannot overwrite the customer's assignment.
        jdbc.update("""
                UPDATE delivery_requests SET helper_id = ?, status = 'ACCEPTED', version = version + 1,
                updated_at = clock_timestamp() AT TIME ZONE 'UTC' WHERE id = ?
                """, application.helperId(), requestId);
        jdbc.update("UPDATE job_applications SET status = 'APPROVED', updated_at = clock_timestamp() WHERE id = ?", applicationId);
        jdbc.update("UPDATE job_applications SET status = 'NOT_SELECTED', updated_at = clock_timestamp() WHERE request_id = ? AND id <> ? AND status = 'PENDING'", requestId, applicationId);
        return requestResponse(requestId);
    }

    @Transactional
    public JobApplicationResponse decline(long requestId, long applicationId, long actor) {
        var request = loadRequest(requestId, true);
        requireCustomer(request, actor);
        var application = loadApplication(requestId, applicationId);
        if (application.status().equals("DECLINED")) return application;
        requireOpen(request); requirePending(application);
        jdbc.update("UPDATE job_applications SET status = 'DECLINED', updated_at = clock_timestamp() WHERE id = ?", applicationId);
        return loadApplication(requestId, applicationId);
    }

    @Transactional
    public JobApplicationResponse withdraw(long requestId, long applicationId, long actor) {
        var request = loadRequest(requestId, true);
        var application = loadApplication(requestId, applicationId);
        if (application.helperId() != actor) throw new AccessDeniedException("Only the applicant may withdraw.");
        if (application.status().equals("WITHDRAWN")) return application;
        requireOpen(request); requirePending(application);
        jdbc.update("UPDATE job_applications SET status = 'WITHDRAWN', updated_at = clock_timestamp() WHERE id = ?", applicationId);
        return loadApplication(requestId, applicationId);
    }

    private Map<String, Object> loadRequest(long id, boolean lock) {
        var rows = jdbc.queryForList("SELECT customer_id, helper_id, status::text FROM delivery_requests WHERE id = ?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Delivery request not found.");
        return rows.getFirst();
    }
    private JobApplicationResponse loadApplication(long requestId, long id) {
        var rows = jdbc.query(DETAILS + " WHERE a.request_id = ? AND a.id = ?", APPLICATION, requestId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Helper request not found.");
        return rows.getFirst();
    }
    private DeliveryRequestResponse requestResponse(long id) {
        var request = requests.findById(id).orElseThrow();
        entityManager.refresh(request);
        return DeliveryRequestResponse.from(request);
    }
    private static long number(Map<String, Object> row, String key) {
        return row.get(key) == null ? 0 : ((Number) row.get(key)).longValue();
    }
    private static void requireCustomer(Map<String, Object> request, long actor) {
        if (number(request, "customer_id") != actor) throw new AccessDeniedException("Customer approval required.");
    }
    private static void requireOpen(Map<String, Object> request) {
        if (request.get("helper_id") != null) throw new RequestAlreadyAcceptedException();
        if (!request.get("status").equals("OPEN")) throw new RequestNotOpenException();
    }
    private static void requirePending(JobApplicationResponse application) {
        if (!application.status().equals("PENDING")) throw new InvalidStatusTransitionException("This helper request is no longer awaiting a decision.");
    }
}
