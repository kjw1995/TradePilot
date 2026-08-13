package com.kjw.tradepilot.instrument.application.service;

import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentSearchServiceTest {
    private final InstrumentRepositoryPort repository = mock(InstrumentRepositoryPort.class);
    private InstrumentSearchService service;

    @BeforeEach
    void setUp() {
        service = new InstrumentSearchService(repository);
    }

    @Test
    void normalizesQueryAndCapsResultLimit() {
        SecurityInstrument naver = new SecurityInstrument(
                Market.KRX, "035420", "NAVER", "KOSPI", "KRW", Instant.parse("2026-08-13T00:00:00Z")
        );
        when(repository.search(Market.KRX, "NAVER", 20)).thenReturn(Flux.just(naver));

        StepVerifier.create(service.search(Market.KRX, "  naver ", 100))
                .expectNext(naver)
                .verifyComplete();

        verify(repository).search(Market.KRX, "NAVER", 20);
    }

    @Test
    void raisesMinimumResultLimitToOne() {
        when(repository.search(Market.KRX, "삼성", 1)).thenReturn(Flux.empty());

        StepVerifier.create(service.search(Market.KRX, "삼성", 0))
                .verifyComplete();

        verify(repository).search(Market.KRX, "삼성", 1);
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> service.search(Market.KRX, "  ", 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }
}
