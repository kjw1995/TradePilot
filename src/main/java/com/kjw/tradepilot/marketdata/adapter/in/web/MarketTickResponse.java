package com.kjw.tradepilot.marketdata.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;

import java.math.BigDecimal;
import java.time.Instant;

record MarketTickResponse(
        String symbol,
        Market market,
        BigDecimal price,
        long volume,
        Instant tradedAt,
        Instant receivedAt,
        String source
) {
    static MarketTickResponse from(MarketTick tick) {
        return new MarketTickResponse(
                tick.symbol(),
                tick.market(),
                tick.price(),
                tick.volume(),
                tick.tradedAt(),
                tick.receivedAt(),
                tick.source()
        );
    }
}
