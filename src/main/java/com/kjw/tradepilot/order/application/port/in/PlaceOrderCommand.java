package com.kjw.tradepilot.order.application.port.in;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderType;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        String accountId,
        Market market,
        String symbol,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal limitPrice,
        String idempotencyKey
) {
}
