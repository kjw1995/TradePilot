package com.kjw.tradepilot.portfolio.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record PortfolioPosition(
        String accountId,
        String symbol,
        Market market,
        String name,
        long quantity,
        BigDecimal averagePrice,
        Instant syncedAt
) {
    public PortfolioPosition {
        accountId = requireText(accountId, "accountId");
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        market = Objects.requireNonNull(market, "market must not be null");
        name = requireText(name, "name");
        averagePrice = Objects.requireNonNull(averagePrice, "averagePrice must not be null");
        syncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (averagePrice.signum() <= 0) {
            throw new IllegalArgumentException("averagePrice must be greater than zero");
        }
    }

    public BigDecimal costBasis() {
        return averagePrice.multiply(BigDecimal.valueOf(quantity));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
