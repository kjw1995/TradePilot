package com.kjw.tradepilot.order.domain;

import com.kjw.tradepilot.marketdata.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record TradeOrder(
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
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt
) {
    public TradeOrder {
        orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        accountId = requireText(accountId, "accountId");
        market = Objects.requireNonNull(market, "market must not be null");
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        name = requireText(name, "name");
        side = Objects.requireNonNull(side, "side must not be null");
        orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than zero");
        if (filledQuantity < 0 || filledQuantity > quantity) {
            throw new IllegalArgumentException("filledQuantity must be between zero and quantity");
        }
        if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("limitPrice must be greater than zero for LIMIT order");
        }
        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("limitPrice must be null for MARKET order");
        }
    }

    public static TradeOrder pending(
            String accountId, Market market, String symbol, String name, OrderSide side,
            OrderType orderType, long quantity, BigDecimal limitPrice, String idempotencyKey, Instant now
    ) {
        return new TradeOrder(
                UUID.randomUUID(), accountId, market, symbol, name, side, orderType, quantity, limitPrice,
                OrderStatus.PENDING, 0, null, idempotencyKey, now, now
        );
    }

    public boolean canExecuteAt(BigDecimal marketPrice) {
        if (status != OrderStatus.PENDING || marketPrice == null || marketPrice.signum() <= 0) return false;
        if (orderType == OrderType.MARKET) return true;
        return side == OrderSide.BUY
                ? marketPrice.compareTo(limitPrice) <= 0
                : marketPrice.compareTo(limitPrice) >= 0;
    }

    public BigDecimal reservationPrice(BigDecimal latestPrice) {
        return orderType == OrderType.LIMIT ? limitPrice : latestPrice;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
