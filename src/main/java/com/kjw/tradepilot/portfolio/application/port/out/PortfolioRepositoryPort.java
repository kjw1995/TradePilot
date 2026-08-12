package com.kjw.tradepilot.portfolio.application.port.out;

import com.kjw.tradepilot.portfolio.domain.PortfolioAccount;
import com.kjw.tradepilot.portfolio.domain.PortfolioPosition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PortfolioRepositoryPort {
    Mono<PortfolioAccount> findAccount(String accountId);

    Flux<PortfolioPosition> findPositions(String accountId);
}
