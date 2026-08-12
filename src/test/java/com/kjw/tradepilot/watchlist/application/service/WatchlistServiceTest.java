package com.kjw.tradepilot.watchlist.application.service;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.application.WatchlistItemAlreadyExistsException;
import com.kjw.tradepilot.watchlist.application.port.out.WatchlistRepositoryPort;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import com.kjw.tradepilot.watchlist.domain.WatchlistKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    private final WatchlistRepositoryPort repository = mock(WatchlistRepositoryPort.class);
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        service = new WatchlistService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsNormalizedItemAtNextOrder() {
        when(repository.find("local-account", Market.KRX, "035420")).thenReturn(Mono.empty());
        when(repository.findAll("local-account")).thenReturn(Flux.just(item(1L, "005930", "삼성전자", 0)));
        when(repository.findNextDisplayOrder("local-account")).thenReturn(Mono.just(1));
        when(repository.save(any())).thenAnswer(invocation -> {
            WatchlistItem newItem = invocation.getArgument(0);
            return Mono.just(new WatchlistItem(
                    2L, newItem.accountId(), newItem.symbol(), newItem.market(), newItem.name(),
                    newItem.displayOrder(), newItem.createdAt()
            ));
        });

        StepVerifier.create(service.addItem(" local-account ", " 035420 ", Market.KRX, "NAVER"))
                .assertNext(item -> {
                    assertThat(item.symbol()).isEqualTo("035420");
                    assertThat(item.displayOrder()).isOne();
                    assertThat(item.createdAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void rejectsDuplicateItem() {
        WatchlistItem existing = item(1L, "005930", "삼성전자", 0);
        when(repository.find("local-account", Market.KRX, "005930")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.addItem("local-account", "005930", Market.KRX, "삼성전자"))
                .expectError(WatchlistItemAlreadyExistsException.class)
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void reordersEveryExistingItem() {
        WatchlistItem samsung = item(1L, "005930", "삼성전자", 0);
        WatchlistItem hynix = item(2L, "000660", "SK하이닉스", 1);
        when(repository.findAll("local-account")).thenReturn(Flux.just(samsung, hynix));
        when(repository.saveOrder(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.reorder("local-account", List.of(
                        new WatchlistKey(Market.KRX, "000660"),
                        new WatchlistKey(Market.KRX, "005930")
                )))
                .assertNext(item -> {
                    assertThat(item.symbol()).isEqualTo("000660");
                    assertThat(item.displayOrder()).isZero();
                })
                .assertNext(item -> {
                    assertThat(item.symbol()).isEqualTo("005930");
                    assertThat(item.displayOrder()).isOne();
                })
                .verifyComplete();
    }

    @Test
    void rejectsPartialReorder() {
        when(repository.findAll("local-account")).thenReturn(Flux.just(
                item(1L, "005930", "삼성전자", 0),
                item(2L, "000660", "SK하이닉스", 1)
        ));

        StepVerifier.create(service.reorder("local-account", List.of(
                        new WatchlistKey(Market.KRX, "005930")
                )))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("every watchlist item"))
                .verify();

        verify(repository, never()).saveOrder(any());
    }

    private WatchlistItem item(Long id, String symbol, String name, int order) {
        return new WatchlistItem(id, "local-account", symbol, Market.KRX, name, order, NOW);
    }
}
