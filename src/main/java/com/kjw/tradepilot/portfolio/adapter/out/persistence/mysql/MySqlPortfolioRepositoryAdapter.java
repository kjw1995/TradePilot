package com.kjw.tradepilot.portfolio.adapter.out.persistence.mysql;

import com.kjw.tradepilot.portfolio.application.port.out.PortfolioRepositoryPort;
import com.kjw.tradepilot.portfolio.domain.PortfolioAccount;
import com.kjw.tradepilot.portfolio.domain.PortfolioPosition;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
class MySqlPortfolioRepositoryAdapter implements PortfolioRepositoryPort {
    private final SpringDataPortfolioAccountRepository accountRepository;
    private final SpringDataPortfolioPositionRepository positionRepository;

    MySqlPortfolioRepositoryAdapter(
            SpringDataPortfolioAccountRepository accountRepository,
            SpringDataPortfolioPositionRepository positionRepository
    ) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    public Mono<PortfolioAccount> findAccount(String accountId) {
        return accountRepository.findById(accountId).map(PortfolioAccountEntity::toDomain);
    }

    @Override
    public Flux<PortfolioPosition> findPositions(String accountId) {
        return positionRepository.findAllByAccountIdOrderBySymbolAsc(accountId)
                .map(PortfolioPositionEntity::toDomain);
    }
}
