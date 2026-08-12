package com.kjw.tradepilot.watchlist.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.util.Locale;
import java.util.Objects;

public record WatchlistKey(Market market, String symbol) {
    public WatchlistKey {
        market = Objects.requireNonNull(market, "market must not be null");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.trim().toUpperCase(Locale.ROOT);
    }
}
