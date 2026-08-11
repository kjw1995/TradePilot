package com.kjw.tradepilot.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record MarketTick(
        String symbol,
        Market market,
        BigDecimal price,
        long volume,
        Instant tradedAt,
        Instant receivedAt,
        String source
) {
    public MarketTick {
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        market = Objects.requireNonNull(market, "market must not be null");
        price = Objects.requireNonNull(price, "price must not be null");
        tradedAt = Objects.requireNonNull(tradedAt, "tradedAt must not be null");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        source = requireText(source, "source").toUpperCase(Locale.ROOT);

        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
