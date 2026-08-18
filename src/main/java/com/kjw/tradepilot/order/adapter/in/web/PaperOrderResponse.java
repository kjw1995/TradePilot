package com.kjw.tradepilot.order.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderStatus;
import com.kjw.tradepilot.order.domain.OrderType;
import com.kjw.tradepilot.order.domain.TradeOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PaperOrderResponse(
        UUID orderId,
        String accountId,
        Market market,
        String symbol,
        String name,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal limitPrice,
        OrderStatus status,
        long filledQuantity,
        BigDecimal averageFillPrice,
        Instant createdAt,
        Instant updatedAt
) {
    static PaperOrderResponse from(TradeOrder order) {
        return new PaperOrderResponse(
                order.orderId(), order.accountId(), order.market(), order.symbol(), order.name(),
                order.side(), order.orderType(), order.quantity(), order.limitPrice(), order.status(),
                order.filledQuantity(), order.averageFillPrice(), order.createdAt(), order.updatedAt()
        );
    }
}
