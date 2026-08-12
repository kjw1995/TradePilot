package com.kjw.tradepilot.portfolio.adapter.out.persistence.mysql;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

interface SpringDataPortfolioAccountRepository extends ReactiveCrudRepository<PortfolioAccountEntity, String> {
}
