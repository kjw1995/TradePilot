package com.kjw.tradepilot.portfolio.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record PositionValuation(
        String symbol,
        Market market,
        String name,
        long quantity,
        BigDecimal averagePrice,
        BigDecimal costBasis,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal profitLoss,
        BigDecimal returnRate,
        boolean quoteAvailable,
        Instant quotedAt
) {
    public static PositionValuation quoted(PortfolioPosition position, BigDecimal currentPrice, Instant quotedAt) {
        BigDecimal costBasis = position.costBasis();
        BigDecimal marketValue = currentPrice.multiply(BigDecimal.valueOf(position.quantity()));
        BigDecimal profitLoss = marketValue.subtract(costBasis);
        BigDecimal returnRate = profitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(costBasis, 4, RoundingMode.HALF_UP);

        return new PositionValuation(
                position.symbol(),
                position.market(),
                position.name(),
                position.quantity(),
                position.averagePrice(),
                costBasis,
                currentPrice,
                marketValue,
                profitLoss,
                returnRate,
                true,
                quotedAt
        );
    }

    public static PositionValuation unavailable(PortfolioPosition position) {
        return new PositionValuation(
                position.symbol(),
                position.market(),
                position.name(),
                position.quantity(),
                position.averagePrice(),
                position.costBasis(),
                null,
                null,
                null,
                null,
                false,
                null
        );
    }
}
