package com.kjw.tradepilot.watchlist.adapter.out.persistence.mysql;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

interface SpringDataWatchlistRepository extends ReactiveCrudRepository<WatchlistItemEntity, Long> {
    Flux<WatchlistItemEntity> findAllByAccountIdOrderByDisplayOrderAsc(String accountId);

    Mono<WatchlistItemEntity> findByAccountIdAndMarketAndSymbol(String accountId, String market, String symbol);

    Mono<WatchlistItemEntity> findFirstByAccountIdOrderByDisplayOrderDesc(String accountId);
}
