package com.kjw.tradepilot.alert.adapter.in.web;

import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.marketdata.domain.Market;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

record CreatePriceAlertRequest(
        @NotNull Market market,
        @NotBlank String symbol,
        @NotNull PriceAlertCondition condition,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal targetPrice
) {
}
