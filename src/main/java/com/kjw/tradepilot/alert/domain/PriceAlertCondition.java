package com.kjw.tradepilot.alert.domain;

import java.math.BigDecimal;

public enum PriceAlertCondition {
    ABOVE {
        @Override
        public boolean matches(BigDecimal marketPrice, BigDecimal targetPrice) {
            return marketPrice.compareTo(targetPrice) >= 0;
        }
    },
    BELOW {
        @Override
        public boolean matches(BigDecimal marketPrice, BigDecimal targetPrice) {
            return marketPrice.compareTo(targetPrice) <= 0;
        }
    };

    public abstract boolean matches(BigDecimal marketPrice, BigDecimal targetPrice);
}
