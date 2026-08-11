package com.kjw.tradepilot.marketdata.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

record MarketTickRequest(
        @NotBlank String symbol,
        @NotNull Market market,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @PositiveOrZero long volume,
        @NotNull Instant tradedAt,
        @NotBlank String source
) {
    MarketTick toDomain() {
        return new MarketTick(symbol, market, price, volume, tradedAt, Instant.now(), source);
    }
}
