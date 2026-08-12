package com.kjw.tradepilot.watchlist.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.application.port.out.WatchlistRepositoryPort;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
class MySqlWatchlistRepositoryAdapter implements WatchlistRepositoryPort {
    private final SpringDataWatchlistRepository repository;

    MySqlWatchlistRepositoryAdapter(SpringDataWatchlistRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<WatchlistItem> findAll(String accountId) {
        return repository.findAllByAccountIdOrderByDisplayOrderAsc(accountId)
                .map(WatchlistItemEntity::toDomain);
    }

    @Override
    public Mono<WatchlistItem> find(String accountId, Market market, String symbol) {
        return repository.findByAccountIdAndMarketAndSymbol(accountId, market.name(), symbol)
                .map(WatchlistItemEntity::toDomain);
    }

    @Override
    public Mono<Integer> findNextDisplayOrder(String accountId) {
        return repository.findFirstByAccountIdOrderByDisplayOrderDesc(accountId)
                .map(item -> item.displayOrder() + 1)
                .defaultIfEmpty(0);
    }

    @Override
    public Mono<WatchlistItem> save(WatchlistItem item) {
        return repository.save(WatchlistItemEntity.from(item)).map(WatchlistItemEntity::toDomain);
    }

    @Override
    public Mono<Boolean> delete(String accountId, Market market, String symbol) {
        return repository.findByAccountIdAndMarketAndSymbol(accountId, market.name(), symbol)
                .flatMap(item -> repository.delete(item).thenReturn(true))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> saveOrder(List<WatchlistItem> items) {
        return Flux.fromIterable(items)
                .concatMap(item -> repository.save(WatchlistItemEntity.from(item)))
                .then();
    }
}
