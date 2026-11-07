package com.localdelivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}
    public record Conversation(long id, long requestId, String requestTitle, String status,
            long customerId, String customerName, long helperId, String helperName,
            boolean canSend, boolean canViewRequest, Instant updatedAt) {}
    public record Message(long id, long conversationId, long senderId, UUID clientMessageId,
            String body, Instant createdAt) {}
    public record SendMessage(@NotNull UUID clientMessageId,
            @NotBlank @Size(max = 2000) String body) {
        public SendMessage { if (body != null && body.isBlank()) body = ""; }
    }
    public record MessagePage(List<Message> items, boolean hasMore) {}
    public record ConversationPage(List<Conversation> items, boolean hasMore) {}
}
