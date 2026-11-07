package com.localdelivery.service;

import com.localdelivery.dto.ChatDtos.*;
import com.localdelivery.exception.InvalidStatusTransitionException;
import com.localdelivery.exception.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private static final String DETAILS = """
            SELECT c.id, c.request_id, c.helper_id, c.updated_at, r.title, r.status::text,
                   r.customer_id, r.helper_id AS assigned_helper_id,
                   customer.name AS customer_name, helper.name AS helper_name
            FROM conversations c JOIN delivery_requests r ON r.id = c.request_id
            JOIN users customer ON customer.id = r.customer_id JOIN users helper ON helper.id = c.helper_id
            """;
    private static final RowMapper<Message> MESSAGE = (rs, row) -> new Message(rs.getLong("id"),
            rs.getLong("conversation_id"), rs.getLong("sender_id"),
            rs.getObject("client_message_id", java.util.UUID.class), rs.getString("body"),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    private final AuthRateLimiter limiter;
    public ChatService(JdbcTemplate jdbc, AuthRateLimiter limiter) {
        this.jdbc = jdbc;
        this.limiter = limiter;
    }

    @Transactional
    public Conversation start(long requestId, long actor) {
        // Serialize creation and messages with acceptance/status updates on the same request row.
        var rows = jdbc.queryForList("SELECT customer_id, helper_id, status::text FROM delivery_requests WHERE id = ? FOR UPDATE", requestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Delivery request not found.");
        var request = rows.getFirst();
        if (((Number) request.get("customer_id")).longValue() == actor)
            throw new AccessDeniedException("Customers reply to helpers from Messages.");
        var existing = jdbc.queryForList("SELECT id FROM conversations WHERE request_id = ? AND helper_id = ?", Long.class, requestId, actor);
        if (!existing.isEmpty()) return get(existing.getFirst(), actor);
        var assigned = (Number) request.get("helper_id");
        if (!writable((String) request.get("status"), assigned == null ? null : assigned.longValue(), actor))
            throw new InvalidStatusTransitionException("This request is no longer available for a new conversation.");
        limiter.chatStart(actor);
        Long id = jdbc.queryForObject("INSERT INTO conversations(request_id, helper_id) VALUES (?, ?) RETURNING id", Long.class, requestId, actor);
        return get(id, actor);
    }

    @Transactional(readOnly = true)
    public ConversationPage list(long actor, int page, Long requestId) {
        var rows = jdbc.query(DETAILS + " WHERE (r.customer_id = ? OR c.helper_id = ?)"
                + (requestId == null ? "" : " AND c.request_id = ?") + " ORDER BY c.id DESC LIMIT 26 OFFSET ?",
                (rs, row) -> conversation(rs, actor), requestId == null
                    ? new Object[]{actor, actor, page * 25L} : new Object[]{actor, actor, requestId, page * 25L});
        return new ConversationPage(rows.stream().limit(25).toList(), rows.size() > 25);
    }

    @Transactional(readOnly = true)
    public Conversation get(long id, long actor) {
        var rows = jdbc.query(DETAILS + " WHERE c.id = ? AND (r.customer_id = ? OR c.helper_id = ?)",
                (rs, row) -> conversation(rs, actor), id, actor, actor);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Conversation not found.");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public MessagePage messages(long id, long actor, Long before, Long after) {
        get(id, actor);
        if (before != null && after != null)
            throw new InvalidStatusTransitionException("Use either before or after, not both.");
        String cursor = before != null ? " AND id < ?" : after != null ? " AND id > ?" : "";
        var rows = jdbc.query("SELECT * FROM chat_messages WHERE conversation_id = ?" + cursor
                + (after == null ? " ORDER BY id DESC" : " ORDER BY id ASC") + " LIMIT 51", MESSAGE,
                before != null ? new Object[]{id, before} : after != null ? new Object[]{id, after} : new Object[]{id});
        var items = new ArrayList<>(rows.stream().limit(50).toList());
        if (after == null) Collections.reverse(items);
        return new MessagePage(items, rows.size() > 50);
    }

    @Transactional
    public Message send(long id, long actor, SendMessage input) {
        // Authorize before acquiring the shared request lock; re-read status after acquiring it.
        var initial = get(id, actor);
        jdbc.queryForObject("SELECT id FROM delivery_requests WHERE id = ? FOR UPDATE", Long.class, initial.requestId());
        var conversation = get(id, actor);
        String body = input.body().strip();
        var previous = jdbc.query("SELECT * FROM chat_messages WHERE conversation_id = ? AND sender_id = ? AND client_message_id = ?",
                MESSAGE, id, actor, input.clientMessageId());
        if (!previous.isEmpty()) {
            if (!previous.getFirst().body().equals(body))
                throw new InvalidStatusTransitionException("This message identifier has already been used.");
            return previous.getFirst();
        }
        if (!conversation.canSend()) throw new InvalidStatusTransitionException("This conversation is now read-only.");
        limiter.chatMessage(actor);
        var saved = jdbc.queryForObject("""
                INSERT INTO chat_messages(conversation_id, sender_id, client_message_id, body)
                VALUES (?, ?, ?, ?) RETURNING *
                """, MESSAGE, id, actor, input.clientMessageId(), body);
        jdbc.update("UPDATE conversations SET updated_at = now() WHERE id = ?", id);
        return saved;
    }

    private static boolean writable(String status, Long assigned, long helper) {
        return status.equals("OPEN") || (assigned != null && assigned == helper
                && !status.equals("DELIVERED") && !status.equals("CANCELLED"));
    }

    private static Conversation conversation(ResultSet rs, long actor) throws SQLException {
        Long assigned = rs.getObject("assigned_helper_id", Long.class);
        String status = rs.getString("status");
        long customer = rs.getLong("customer_id");
        return new Conversation(rs.getLong("id"), rs.getLong("request_id"), rs.getString("title"), status,
                customer, rs.getString("customer_name"), rs.getLong("helper_id"), rs.getString("helper_name"),
                writable(status, assigned, rs.getLong("helper_id")),
                status.equals("OPEN") || customer == actor || (assigned != null && assigned == actor),
                rs.getTimestamp("updated_at").toInstant());
    }
}
