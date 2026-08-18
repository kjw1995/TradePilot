package com.kjw.tradepilot.order.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

record PlacePaperOrderRequest(
        @NotNull Market market,
        @NotBlank String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @Positive long quantity,
        BigDecimal limitPrice,
        @NotBlank String idempotencyKey
) {
}
