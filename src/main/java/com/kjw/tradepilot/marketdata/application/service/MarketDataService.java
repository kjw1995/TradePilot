package com.kjw.tradepilot.marketdata.application.service;

import com.kjw.tradepilot.marketdata.application.port.in.IngestMarketTickUseCase;
import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.marketdata.application.port.in.MarketDataStreamQuery;
import com.kjw.tradepilot.marketdata.application.port.out.LatestQuoteCachePort;
import com.kjw.tradepilot.marketdata.application.port.out.MarketEventPublisherPort;
import com.kjw.tradepilot.marketdata.application.port.out.MarketTickRepositoryPort;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Service
public class MarketDataService implements IngestMarketTickUseCase, MarketDataQuery, MarketDataStreamQuery {
    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final MarketTickRepositoryPort repository;
    private final LatestQuoteCachePort cache;
    private final MarketEventPublisherPort eventPublisher;

    public MarketDataService(
            MarketTickRepositoryPort repository,
            LatestQuoteCachePort cache,
            MarketEventPublisherPort eventPublisher
    ) {
        this.repository = repository;
        this.cache = cache;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<MarketTick> ingest(MarketTick tick) {
        return repository.save(tick)
                .flatMap(saved -> updateCache(saved)
                        .then(eventPublisher.publish(saved))
                        .thenReturn(saved));
    }

    @Override
    public Mono<MarketTick> getLatest(Market market, String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);

        return cache.get(market, normalizedSymbol)
                .onErrorResume(error -> {
                    log.warn("Latest quote cache read failed: market={}, symbol={}", market, normalizedSymbol, error);
                    return Mono.empty();
                })
                .switchIfEmpty(repository.findLatest(market, normalizedSymbol)
                        .flatMap(tick -> updateCache(tick).thenReturn(tick)));
    }

    @Override
    public Flux<MarketTick> stream() {
        return eventPublisher.stream();
    }

    private Mono<Void> updateCache(MarketTick tick) {
        return cache.put(tick)
                .onErrorResume(error -> {
                    // Redis is an optimization, not the source of truth. MySQL persistence remains successful.
                    log.warn("Latest quote cache update failed: market={}, symbol={}", tick.market(), tick.symbol(), error);
                    return Mono.empty();
                });
    }
}
