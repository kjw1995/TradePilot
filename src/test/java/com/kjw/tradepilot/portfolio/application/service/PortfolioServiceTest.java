package com.kjw.tradepilot.portfolio.application.service;

import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import com.kjw.tradepilot.portfolio.application.port.out.PortfolioRepositoryPort;
import com.kjw.tradepilot.portfolio.domain.PortfolioAccount;
import com.kjw.tradepilot.portfolio.domain.PortfolioPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {
    private static final Instant SYNCED_AT = Instant.parse("2026-08-12T00:00:00Z");

    private final PortfolioRepositoryPort repository = mock(PortfolioRepositoryPort.class);
    private final MarketDataQuery marketDataQuery = mock(MarketDataQuery.class);

    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(repository, marketDataQuery);
    }

    @Test
    void comparesPositionsWithLatestQuotesAndKeepsUnavailablePosition() {
        PortfolioAccount account = new PortfolioAccount(
                "local-account", "내 투자계좌", "LOCAL", "1234-****",
                BigDecimal.valueOf(3_500_000), "KRW", SYNCED_AT
        );
        PortfolioPosition samsung = new PortfolioPosition(
                "local-account", "005930", Market.KRX, "삼성전자",
                25, BigDecimal.valueOf(76_000), SYNCED_AT
        );
        PortfolioPosition hynix = new PortfolioPosition(
                "local-account", "000660", Market.KRX, "SK하이닉스",
                8, BigDecimal.valueOf(205_000), SYNCED_AT
        );
        MarketTick samsungQuote = new MarketTick(
                "005930", Market.KRX, BigDecimal.valueOf(80_000), 10,
                SYNCED_AT, SYNCED_AT, "LOCAL"
        );

        when(repository.findAccount("local-account")).thenReturn(Mono.just(account));
        when(repository.findPositions("local-account")).thenReturn(Flux.just(samsung, hynix));
        when(marketDataQuery.getLatest(Market.KRX, "005930")).thenReturn(Mono.just(samsungQuote));
        when(marketDataQuery.getLatest(Market.KRX, "000660")).thenReturn(Mono.empty());

        StepVerifier.create(service.getSnapshot("local-account"))
                .assertNext(snapshot -> {
                    assertThat(snapshot.positions()).hasSize(2);
                    assertThat(snapshot.positions().getFirst().symbol()).isEqualTo("000660");
                    assertThat(snapshot.positions().getFirst().quoteAvailable()).isFalse();
                    assertThat(snapshot.positions().getLast().profitLoss())
                            .isEqualByComparingTo("100000");
                    assertThat(snapshot.totals().investedAmount())
                            .isEqualByComparingTo("3540000");
                    assertThat(snapshot.totals().evaluationAmount())
                            .isEqualByComparingTo("2000000");
                    assertThat(snapshot.totals().totalAssets())
                            .isEqualByComparingTo("5500000");
                    assertThat(snapshot.totals().profitLoss())
                            .isEqualByComparingTo("100000");
                    assertThat(snapshot.totals().returnRate())
                            .isEqualByComparingTo("5.2632");
                    assertThat(snapshot.totals().valuedPositionCount()).isOne();
                    assertThat(snapshot.totals().totalPositionCount()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenAccountDoesNotExist() {
        when(repository.findAccount("missing-account")).thenReturn(Mono.empty());

        StepVerifier.create(service.getSnapshot("missing-account"))
                .verifyComplete();
    }
}
