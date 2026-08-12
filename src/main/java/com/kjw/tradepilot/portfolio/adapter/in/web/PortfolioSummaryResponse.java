package com.kjw.tradepilot.portfolio.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.portfolio.domain.PortfolioSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record PortfolioSummaryResponse(
        AccountResponse account,
        TotalsResponse totals,
        List<PositionResponse> positions
) {
    static PortfolioSummaryResponse from(PortfolioSnapshot snapshot) {
        return new PortfolioSummaryResponse(
                new AccountResponse(
                        snapshot.account().accountId(),
                        snapshot.account().displayName(),
                        snapshot.account().broker(),
                        snapshot.account().maskedAccountNumber(),
                        snapshot.account().currency(),
                        snapshot.account().syncedAt()
                ),
                new TotalsResponse(
                        snapshot.totals().cashBalance(),
                        snapshot.totals().investedAmount(),
                        snapshot.totals().evaluationAmount(),
                        snapshot.totals().totalAssets(),
                        snapshot.totals().profitLoss(),
                        snapshot.totals().returnRate(),
                        snapshot.totals().valuedPositionCount(),
                        snapshot.totals().totalPositionCount()
                ),
                snapshot.positions().stream()
                        .map(position -> new PositionResponse(
                                position.symbol(),
                                position.market(),
                                position.name(),
                                position.quantity(),
                                position.averagePrice(),
                                position.costBasis(),
                                position.currentPrice(),
                                position.marketValue(),
                                position.profitLoss(),
                                position.returnRate(),
                                position.quoteAvailable(),
                                position.quotedAt()
                        ))
                        .toList()
        );
    }

    record AccountResponse(
            String accountId,
            String displayName,
            String broker,
            String maskedAccountNumber,
            String currency,
            Instant syncedAt
    ) {
    }

    record TotalsResponse(
            BigDecimal cashBalance,
            BigDecimal investedAmount,
            BigDecimal evaluationAmount,
            BigDecimal totalAssets,
            BigDecimal profitLoss,
            BigDecimal returnRate,
            int valuedPositionCount,
            int totalPositionCount
    ) {
    }

    record PositionResponse(
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
    }
}
