package com.kjw.tradepilot.marketdata.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Table("market_ticks")
public record MarketTickEntity(
        @Id Long id,
        String symbol,
        String market,
        BigDecimal price,
        long volume,
        LocalDateTime tradedAt,
        LocalDateTime receivedAt,
        String source
) {
    static MarketTickEntity from(MarketTick tick) {
        return new MarketTickEntity(
                null,
                tick.symbol(),
                tick.market().name(),
                tick.price(),
                tick.volume(),
                LocalDateTime.ofInstant(tick.tradedAt(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(tick.receivedAt(), ZoneOffset.UTC),
                tick.source()
        );
    }

    MarketTick toDomain() {
        return new MarketTick(
                symbol,
                Market.valueOf(market),
                price,
                volume,
                tradedAt.toInstant(ZoneOffset.UTC),
                receivedAt.toInstant(ZoneOffset.UTC),
                source
        );
    }
}
