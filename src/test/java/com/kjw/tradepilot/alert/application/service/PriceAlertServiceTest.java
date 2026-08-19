package com.kjw.tradepilot.alert.application.service;

import com.kjw.tradepilot.alert.application.PriceAlertLimitExceededException;
import com.kjw.tradepilot.alert.application.PriceAlertNotFoundException;
import com.kjw.tradepilot.alert.application.port.in.CreatePriceAlertCommand;
import com.kjw.tradepilot.alert.application.port.out.PriceAlertEventPublisherPort;
import com.kjw.tradepilot.alert.application.port.out.PriceAlertRepositoryPort;
import com.kjw.tradepilot.alert.domain.PriceAlert;
import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.alert.domain.PriceAlertStatus;
import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceAlertServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private final PriceAlertRepositoryPort repository = mock(PriceAlertRepositoryPort.class);
    private final InstrumentRepositoryPort instrumentRepository = mock(InstrumentRepositoryPort.class);
    private final PriceAlertEventPublisherPort eventPublisher = mock(PriceAlertEventPublisherPort.class);
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        service = new PriceAlertService(
                repository, instrumentRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    @Test
    void createsActiveAlertForKnownInstrument() {
        arrangeAvailableAccount();
        when(instrumentRepository.find(Market.KRX, "005930")).thenReturn(Mono.just(instrument()));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(command(PriceAlertCondition.ABOVE, new BigDecimal("85000"))))
                .assertNext(alert -> {
                    assertThat(alert.status()).isEqualTo(PriceAlertStatus.ACTIVE);
                    assertThat(alert.name()).isEqualTo("삼성전자");
                    assertThat(alert.targetPrice()).isEqualByComparingTo("85000");
                })
                .verifyComplete();

        verify(eventPublisher).publish(any());
    }

    @Test
    void rejectsAlertWhenActiveLimitIsReached() {
        when(repository.accountExists("local-account")).thenReturn(Mono.just(true));
        when(repository.countActive("local-account")).thenReturn(Mono.just(30L));

        StepVerifier.create(service.create(command(PriceAlertCondition.BELOW, new BigDecimal("70000"))))
                .expectError(PriceAlertLimitExceededException.class)
                .verify();

        verify(instrumentRepository, never()).find(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void triggersMatchingAboveAlert() {
        PriceAlert active = active(PriceAlertCondition.ABOVE, new BigDecimal("80000"));
        PriceAlert triggered = triggered(active, new BigDecimal("80100"));
        when(repository.findActive(Market.KRX, "005930")).thenReturn(Flux.just(active));
        when(repository.trigger(active.alertId(), new BigDecimal("80100"), NOW)).thenReturn(Mono.just(triggered));

        StepVerifier.create(service.evaluate(tick(new BigDecimal("80100"))))
                .verifyComplete();

        verify(repository).trigger(active.alertId(), new BigDecimal("80100"), NOW);
        verify(eventPublisher).publish(triggered);
    }

    @Test
    void ignoresAlertWhenPriceDoesNotMatch() {
        PriceAlert active = active(PriceAlertCondition.BELOW, new BigDecimal("70000"));
        when(repository.findActive(Market.KRX, "005930")).thenReturn(Flux.just(active));

        StepVerifier.create(service.evaluate(tick(new BigDecimal("80100"))))
                .verifyComplete();

        verify(repository, never()).trigger(any(), any(), any());
    }

    @Test
    void reactivatesTriggeredAlertWhenCapacityIsAvailable() {
        UUID alertId = UUID.randomUUID();
        PriceAlert reactivated = new PriceAlert(
                alertId, "local-account", Market.KRX, "005930", "삼성전자",
                PriceAlertCondition.ABOVE, new BigDecimal("85000"), PriceAlertStatus.ACTIVE,
                null, null, NOW.minusSeconds(60), NOW
        );
        arrangeAvailableAccount();
        when(repository.reactivate("local-account", alertId, NOW)).thenReturn(Mono.just(reactivated));

        StepVerifier.create(service.reactivate("local-account", alertId))
                .assertNext(alert -> assertThat(alert.status()).isEqualTo(PriceAlertStatus.ACTIVE))
                .verifyComplete();

        verify(eventPublisher).publish(reactivated);
    }

    @Test
    void reportsMissingAlertWhenDeleteChangesNothing() {
        UUID alertId = UUID.randomUUID();
        when(repository.delete("local-account", alertId)).thenReturn(Mono.just(false));

        StepVerifier.create(service.delete("local-account", alertId))
                .expectError(PriceAlertNotFoundException.class)
                .verify();
    }

    private void arrangeAvailableAccount() {
        when(repository.accountExists("local-account")).thenReturn(Mono.just(true));
        when(repository.countActive("local-account")).thenReturn(Mono.just(0L));
    }

    private CreatePriceAlertCommand command(PriceAlertCondition condition, BigDecimal targetPrice) {
        return new CreatePriceAlertCommand("local-account", Market.KRX, "005930", condition, targetPrice);
    }

    private SecurityInstrument instrument() {
        return new SecurityInstrument(Market.KRX, "005930", "삼성전자", "KRX", "KRW", NOW);
    }

    private PriceAlert active(PriceAlertCondition condition, BigDecimal targetPrice) {
        return PriceAlert.active("local-account", Market.KRX, "005930", "삼성전자", condition, targetPrice, NOW);
    }

    private PriceAlert triggered(PriceAlert alert, BigDecimal price) {
        return new PriceAlert(
                alert.alertId(), alert.accountId(), alert.market(), alert.symbol(), alert.name(), alert.condition(),
                alert.targetPrice(), PriceAlertStatus.TRIGGERED, price, NOW, alert.createdAt(), NOW
        );
    }

    private MarketTick tick(BigDecimal price) {
        return new MarketTick("005930", Market.KRX, price, 100, NOW, NOW, "TEST");
    }
}
