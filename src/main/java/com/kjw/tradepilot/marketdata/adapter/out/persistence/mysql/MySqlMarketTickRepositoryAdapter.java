package com.kjw.tradepilot.marketdata.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.application.port.out.MarketTickRepositoryPort;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
class MySqlMarketTickRepositoryAdapter implements MarketTickRepositoryPort {
    private final SpringDataMarketTickRepository repository;

    MySqlMarketTickRepositoryAdapter(SpringDataMarketTickRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<MarketTick> save(MarketTick tick) {
        return repository.save(MarketTickEntity.from(tick)).map(MarketTickEntity::toDomain);
    }

    @Override
    public Mono<MarketTick> findLatest(Market market, String symbol) {
        return repository.findLatest(market.name(), symbol).map(MarketTickEntity::toDomain);
    }
}
