package com.kjw.tradepilot.order.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.TradeExecution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TradeExecutionResponse(
        UUID executionId,
        UUID orderId,
        Market market,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        Instant executedAt
) {
    static TradeExecutionResponse from(TradeExecution execution) {
        return new TradeExecutionResponse(
                execution.executionId(), execution.orderId(), execution.market(), execution.symbol(),
                execution.side(), execution.quantity(), execution.price(), execution.executedAt()
        );
    }
}
