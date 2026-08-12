package com.kjw.tradepilot.portfolio.domain;

import java.math.BigDecimal;

public record PortfolioTotals(
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
