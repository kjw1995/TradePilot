package com.kjw.tradepilot.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record PortfolioAccount(
        String accountId,
        String displayName,
        String broker,
        String maskedAccountNumber,
        BigDecimal cashBalance,
        String currency,
        Instant syncedAt
) {
    public PortfolioAccount {
        accountId = requireText(accountId, "accountId");
        displayName = requireText(displayName, "displayName");
        broker = requireText(broker, "broker").toUpperCase(Locale.ROOT);
        maskedAccountNumber = requireText(maskedAccountNumber, "maskedAccountNumber");
        cashBalance = Objects.requireNonNull(cashBalance, "cashBalance must not be null");
        currency = requireText(currency, "currency").toUpperCase(Locale.ROOT);
        syncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");

        if (cashBalance.signum() < 0) {
            throw new IllegalArgumentException("cashBalance must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
