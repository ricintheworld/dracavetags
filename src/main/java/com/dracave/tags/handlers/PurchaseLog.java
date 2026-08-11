package com.dracave.tags.handlers;

import java.util.UUID;

public record PurchaseLog(
        UUID operationId,
        UUID playerId,
        String tagId,
        String currency,
        java.math.BigDecimal amount,
        String state,
        String failureReason,
        boolean refunded,
        long createdAt,
        long updatedAt,
        Long completedAt
) {
}
