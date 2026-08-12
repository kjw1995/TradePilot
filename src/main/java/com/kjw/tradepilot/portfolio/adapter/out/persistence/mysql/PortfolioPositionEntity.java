package com.kjw.tradepilot.portfolio.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.portfolio.domain.PortfolioPosition;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Table("portfolio_positions")
record PortfolioPositionEntity(
        @Id Long id,
        String accountId,
        String symbol,
        String market,
        String name,
        long quantity,
        BigDecimal averagePrice,
        LocalDateTime syncedAt
) {
    PortfolioPosition toDomain() {
        return new PortfolioPosition(
                accountId,
                symbol,
                Market.valueOf(market),
                name,
                quantity,
                averagePrice,
                syncedAt.toInstant(ZoneOffset.UTC)
        );
    }
}
