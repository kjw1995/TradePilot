package com.kjw.tradepilot.alert.adapter.in.web;

import com.kjw.tradepilot.alert.domain.PriceAlert;
import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.alert.domain.PriceAlertStatus;
import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PriceAlertResponse(
        UUID alertId,
        String accountId,
        Market market,
        String symbol,
        String name,
        PriceAlertCondition condition,
        BigDecimal targetPrice,
        PriceAlertStatus status,
        BigDecimal lastTriggeredPrice,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
    static PriceAlertResponse from(PriceAlert alert) {
        return new PriceAlertResponse(
                alert.alertId(), alert.accountId(), alert.market(), alert.symbol(), alert.name(),
                alert.condition(), alert.targetPrice(), alert.status(), alert.lastTriggeredPrice(),
                alert.lastTriggeredAt(), alert.createdAt(), alert.updatedAt()
        );
    }
}
