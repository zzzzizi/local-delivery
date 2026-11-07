package com.localdelivery.dto;

import java.time.Instant;
import java.util.List;

public record JobApplicationResponse(long id, long requestId, String requestTitle, String requestStatus,
        long customerId, long helperId, String helperName, String status, Instant createdAt, Instant updatedAt) {
    public record Page(List<JobApplicationResponse> items, boolean hasMore) {}
}
