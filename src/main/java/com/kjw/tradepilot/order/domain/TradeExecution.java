package com.kjw.tradepilot.order.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TradeExecution(
        UUID executionId,
        UUID orderId,
        String accountId,
        Market market,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        Instant executedAt
) {
    public TradeExecution {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(market, "market must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId must not be blank");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol must not be blank");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than zero");
        if (price.signum() <= 0) throw new IllegalArgumentException("price must be greater than zero");
    }

    public static TradeExecution from(TradeOrder order, BigDecimal price, Instant executedAt) {
        return new TradeExecution(
                UUID.randomUUID(), order.orderId(), order.accountId(), order.market(), order.symbol(),
                order.side(), order.quantity(), price, executedAt
        );
    }
}
