package com.kjw.tradepilot.portfolio.application.port.in;

import com.kjw.tradepilot.portfolio.domain.PortfolioSnapshot;
import reactor.core.publisher.Mono;

public interface PortfolioQuery {
    Mono<PortfolioSnapshot> getSnapshot(String accountId);
}
