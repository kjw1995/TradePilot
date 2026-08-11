package com.kjw.tradepilot.marketdata.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketTickTest {
    @Test
    void normalizesSymbolAndSource() {
        MarketTick tick = new MarketTick(
                "  aapl ", Market.NASDAQ, BigDecimal.valueOf(200), 10,
                Instant.parse("2026-08-11T00:00:00Z"), Instant.parse("2026-08-11T00:00:01Z"), " broker "
        );

        assertThat(tick.symbol()).isEqualTo("AAPL");
        assertThat(tick.source()).isEqualTo("BROKER");
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> new MarketTick(
                "005930", Market.KRX, BigDecimal.ZERO, 10, Instant.now(), Instant.now(), "LOCAL"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("price must be greater than zero");
    }
}
