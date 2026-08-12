package com.kjw.tradepilot.portfolio.adapter.out.persistence.mysql;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

interface SpringDataPortfolioPositionRepository extends ReactiveCrudRepository<PortfolioPositionEntity, Long> {
    Flux<PortfolioPositionEntity> findAllByAccountIdOrderBySymbolAsc(String accountId);
}
