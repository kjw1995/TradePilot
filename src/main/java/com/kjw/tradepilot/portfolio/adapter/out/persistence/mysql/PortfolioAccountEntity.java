package com.kjw.tradepilot.portfolio.adapter.out.persistence.mysql;

import com.kjw.tradepilot.portfolio.domain.PortfolioAccount;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Table("portfolio_accounts")
record PortfolioAccountEntity(
        @Id String accountId,
        String displayName,
        String broker,
        String maskedAccountNumber,
        BigDecimal cashBalance,
        String currency,
        LocalDateTime syncedAt
) {
    PortfolioAccount toDomain() {
        return new PortfolioAccount(
                accountId,
                displayName,
                broker,
                maskedAccountNumber,
                cashBalance,
                currency,
                syncedAt.toInstant(ZoneOffset.UTC)
        );
    }
}
