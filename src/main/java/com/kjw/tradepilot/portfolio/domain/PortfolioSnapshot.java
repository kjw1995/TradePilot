package com.kjw.tradepilot.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record PortfolioSnapshot(
        PortfolioAccount account,
        PortfolioTotals totals,
        List<PositionValuation> positions
) {
    public static PortfolioSnapshot from(PortfolioAccount account, List<PositionValuation> positions) {
        List<PositionValuation> immutablePositions = List.copyOf(positions);
        BigDecimal investedAmount = immutablePositions.stream()
                .map(PositionValuation::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PositionValuation> valuedPositions = immutablePositions.stream()
                .filter(PositionValuation::quoteAvailable)
                .toList();
        BigDecimal valuedCost = valuedPositions.stream()
                .map(PositionValuation::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal evaluationAmount = valuedPositions.stream()
                .map(PositionValuation::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitLoss = evaluationAmount.subtract(valuedCost);
        BigDecimal returnRate = valuedCost.signum() == 0
                ? BigDecimal.ZERO
                : profitLoss.multiply(BigDecimal.valueOf(100))
                .divide(valuedCost, 4, RoundingMode.HALF_UP);

        PortfolioTotals totals = new PortfolioTotals(
                account.cashBalance(),
                investedAmount,
                evaluationAmount,
                account.cashBalance().add(evaluationAmount),
                profitLoss,
                returnRate,
                valuedPositions.size(),
                immutablePositions.size()
        );
        return new PortfolioSnapshot(account, totals, immutablePositions);
    }
}
