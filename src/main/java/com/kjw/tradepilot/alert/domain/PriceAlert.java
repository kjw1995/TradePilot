package com.kjw.tradepilot.alert.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PriceAlert(
        UUID alertId,
        String accountId,
        Market market,
        String symbol,
        String name,
        PriceAlertCondition condition,
        BigDecimal targetPrice,
        PriceAlertStatus status,
        BigDecimal lastTriggeredPrice,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public PriceAlert {
        alertId = Objects.requireNonNull(alertId, "alertId must not be null");
        accountId = requireText(accountId, "accountId");
        market = Objects.requireNonNull(market, "market must not be null");
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        name = requireText(name, "name");
        condition = Objects.requireNonNull(condition, "condition must not be null");
        targetPrice = Objects.requireNonNull(targetPrice, "targetPrice must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (targetPrice.signum() <= 0) throw new IllegalArgumentException("targetPrice must be greater than zero");
        if (status == PriceAlertStatus.TRIGGERED && (lastTriggeredPrice == null || lastTriggeredAt == null)) {
            throw new IllegalArgumentException("triggered alert must have trigger price and time");
        }
    }

    public static PriceAlert active(
            String accountId,
            Market market,
            String symbol,
            String name,
            PriceAlertCondition condition,
            BigDecimal targetPrice,
            Instant now
    ) {
        return new PriceAlert(
                UUID.randomUUID(), accountId, market, symbol, name, condition, targetPrice,
                PriceAlertStatus.ACTIVE, null, null, now, now
        );
    }

    public boolean isTriggeredBy(BigDecimal marketPrice) {
        return status == PriceAlertStatus.ACTIVE
                && marketPrice != null
                && marketPrice.signum() > 0
                && condition.matches(marketPrice, targetPrice);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
