package com.kjw.tradepilot.watchlist.application.port.in;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import com.kjw.tradepilot.watchlist.domain.WatchlistKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WatchlistUseCase {
    Flux<WatchlistItem> getWatchlist(String accountId);

    Mono<WatchlistItem> addItem(String accountId, String symbol, Market market, String name);

    Mono<Boolean> removeItem(String accountId, Market market, String symbol);

    Flux<WatchlistItem> reorder(String accountId, List<WatchlistKey> orderedItems);
}
