package com.kjw.tradepilot.marketdata.application.service;

import com.kjw.tradepilot.marketdata.application.port.out.LatestQuoteCachePort;
import com.kjw.tradepilot.marketdata.application.port.out.MarketEventPublisherPort;
import com.kjw.tradepilot.marketdata.application.port.out.MarketTickRepositoryPort;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataServiceTest {
    private final MarketTickRepositoryPort repository = mock(MarketTickRepositoryPort.class);
    private final LatestQuoteCachePort cache = mock(LatestQuoteCachePort.class);
    private final MarketEventPublisherPort publisher = mock(MarketEventPublisherPort.class);

    private MarketDataService service;

    @BeforeEach
    void setUp() {
        service = new MarketDataService(repository, cache, publisher);
        when(publisher.stream()).thenReturn(Flux.empty());
    }

    @Test
    void persistsBeforePublishingTick() {
        MarketTick tick = tick();
        when(repository.save(tick)).thenReturn(Mono.just(tick));
        when(cache.put(tick)).thenReturn(Mono.empty());
        when(publisher.publish(tick)).thenReturn(Mono.empty());

        StepVerifier.create(service.ingest(tick))
                .expectNext(tick)
                .verifyComplete();

        verify(repository).save(tick);
        verify(cache).put(tick);
        verify(publisher).publish(tick);
    }

    @Test
    void fallsBackToMySqlWhenRedisIsUnavailable() {
        MarketTick tick = tick();
        when(cache.get(Market.KRX, "005930")).thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(repository.findLatest(Market.KRX, "005930")).thenReturn(Mono.just(tick));
        when(cache.put(tick)).thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(service.getLatest(Market.KRX, "005930"))
                .expectNext(tick)
                .verifyComplete();
    }

    private MarketTick tick() {
        return new MarketTick(
                "005930", Market.KRX, BigDecimal.valueOf(81_000), 10,
                Instant.parse("2026-08-11T00:00:00Z"), Instant.parse("2026-08-11T00:00:01Z"), "LOCAL"
        );
    }
}
