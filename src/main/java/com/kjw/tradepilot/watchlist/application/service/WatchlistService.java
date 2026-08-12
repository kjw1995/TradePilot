package com.kjw.tradepilot.watchlist.application.service;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.application.WatchlistItemAlreadyExistsException;
import com.kjw.tradepilot.watchlist.application.port.in.WatchlistUseCase;
import com.kjw.tradepilot.watchlist.application.port.out.WatchlistRepositoryPort;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import com.kjw.tradepilot.watchlist.domain.WatchlistKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class WatchlistService implements WatchlistUseCase {
    private static final int MAX_WATCHLIST_SIZE = 30;

    private final WatchlistRepositoryPort repository;
    private final Clock clock;

    @Autowired
    public WatchlistService(WatchlistRepositoryPort repository) {
        this(repository, Clock.systemUTC());
    }

    WatchlistService(WatchlistRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Flux<WatchlistItem> getWatchlist(String accountId) {
        return repository.findAll(normalizeAccountId(accountId));
    }

    @Override
    @Transactional
    public Mono<WatchlistItem> addItem(String accountId, String symbol, Market market, String name) {
        String normalizedAccountId = normalizeAccountId(accountId);
        String normalizedSymbol = normalizeSymbol(symbol);

        return repository.find(normalizedAccountId, market, normalizedSymbol)
                .flatMap(existing -> Mono.<WatchlistItem>error(
                        new WatchlistItemAlreadyExistsException(normalizedSymbol)))
                .switchIfEmpty(Mono.defer(() -> repository.findAll(normalizedAccountId)
                        .count()
                        .flatMap(count -> {
                            if (count >= MAX_WATCHLIST_SIZE) {
                                return Mono.error(new IllegalArgumentException(
                                        "Watchlist can contain at most " + MAX_WATCHLIST_SIZE + " items"));
                            }
                            return repository.findNextDisplayOrder(normalizedAccountId)
                                    .map(order -> WatchlistItem.newItem(
                                            normalizedAccountId,
                                            normalizedSymbol,
                                            market,
                                            name,
                                            order,
                                            clock.instant()
                                    ))
                                    .flatMap(repository::save);
                        })));
    }

    @Override
    @Transactional
    public Mono<Boolean> removeItem(String accountId, Market market, String symbol) {
        return repository.delete(normalizeAccountId(accountId), market, normalizeSymbol(symbol));
    }

    @Override
    @Transactional
    public Flux<WatchlistItem> reorder(String accountId, List<WatchlistKey> orderedItems) {
        String normalizedAccountId = normalizeAccountId(accountId);
        if (orderedItems == null) {
            return Flux.error(new IllegalArgumentException("items must not be null"));
        }

        Set<WatchlistKey> requestedKeys = new HashSet<>(orderedItems);
        if (requestedKeys.size() != orderedItems.size()) {
            return Flux.error(new IllegalArgumentException("items must not contain duplicates"));
        }

        return repository.findAll(normalizedAccountId)
                .collectList()
                .flatMapMany(existingItems -> reorderExisting(existingItems, orderedItems, requestedKeys));
    }

    private Flux<WatchlistItem> reorderExisting(
            List<WatchlistItem> existingItems,
            List<WatchlistKey> orderedItems,
            Set<WatchlistKey> requestedKeys
    ) {
        Set<WatchlistKey> existingKeys = existingItems.stream()
                .map(item -> new WatchlistKey(item.market(), item.symbol()))
                .collect(Collectors.toSet());
        if (!existingKeys.equals(requestedKeys)) {
            return Flux.error(new IllegalArgumentException(
                    "items must contain every watchlist item exactly once"));
        }

        var existingByKey = existingItems.stream().collect(Collectors.toMap(
                item -> new WatchlistKey(item.market(), item.symbol()),
                item -> item
        ));
        List<WatchlistItem> reordered = IntStream.range(0, orderedItems.size())
                .mapToObj(index -> existingByKey.get(orderedItems.get(index)).withDisplayOrder(index))
                .toList();
        return repository.saveOrder(reordered).thenMany(Flux.fromIterable(reordered));
    }

    private String normalizeAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        return accountId.trim();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
