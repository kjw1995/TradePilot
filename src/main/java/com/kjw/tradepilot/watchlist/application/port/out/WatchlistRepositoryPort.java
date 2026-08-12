package com.kjw.tradepilot.watchlist.application.port.out;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WatchlistRepositoryPort {
    Flux<WatchlistItem> findAll(String accountId);

    Mono<WatchlistItem> find(String accountId, Market market, String symbol);

    Mono<Integer> findNextDisplayOrder(String accountId);

    Mono<WatchlistItem> save(WatchlistItem item);

    Mono<Boolean> delete(String accountId, Market market, String symbol);

    Mono<Void> saveOrder(List<WatchlistItem> items);
}
