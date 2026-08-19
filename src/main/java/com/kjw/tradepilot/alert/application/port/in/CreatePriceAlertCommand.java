package com.kjw.tradepilot.alert.application.port.in;

import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;

public record CreatePriceAlertCommand(
        String accountId,
        Market market,
        String symbol,
        PriceAlertCondition condition,
        BigDecimal targetPrice
) {
}
