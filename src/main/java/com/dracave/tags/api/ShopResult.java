package com.dracave.tags.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopResult(
        ShopStatus status,
        UUID operationId,
        String tagId,
        String currency,
        BigDecimal amount,
        String detail
) {
}
