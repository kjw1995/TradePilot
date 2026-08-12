package com.kjw.tradepilot.watchlist.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record WatchlistItem(
        Long id,
        String accountId,
        String symbol,
        Market market,
        String name,
        int displayOrder,
        Instant createdAt
) {
    public WatchlistItem {
        accountId = requireText(accountId, "accountId");
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        market = Objects.requireNonNull(market, "market must not be null");
        name = requireText(name, "name");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
    }

    public static WatchlistItem newItem(
            String accountId,
            String symbol,
            Market market,
            String name,
            int displayOrder,
            Instant createdAt
    ) {
        return new WatchlistItem(null, accountId, symbol, market, name, displayOrder, createdAt);
    }

    public WatchlistItem withDisplayOrder(int newDisplayOrder) {
        return new WatchlistItem(id, accountId, symbol, market, name, newDisplayOrder, createdAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
