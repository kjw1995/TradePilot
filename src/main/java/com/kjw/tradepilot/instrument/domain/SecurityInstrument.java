package com.kjw.tradepilot.instrument.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record SecurityInstrument(
        Market market,
        String symbol,
        String name,
        String exchange,
        String currency,
        Instant updatedAt
) {
    public SecurityInstrument {
        market = Objects.requireNonNull(market, "market must not be null");
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        name = requireText(name, "name");
        exchange = requireText(exchange, "exchange").toUpperCase(Locale.ROOT);
        currency = requireText(currency, "currency").toUpperCase(Locale.ROOT);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
