package com.kjw.tradepilot.alert.application.service;

import com.kjw.tradepilot.alert.application.PriceAlertLimitExceededException;
import com.kjw.tradepilot.alert.application.PriceAlertNotFoundException;
import com.kjw.tradepilot.alert.application.PriceAlertStateException;
import com.kjw.tradepilot.alert.application.port.in.CreatePriceAlertCommand;
import com.kjw.tradepilot.alert.application.port.in.PriceAlertUseCase;
import com.kjw.tradepilot.alert.application.port.out.PriceAlertEventPublisherPort;
import com.kjw.tradepilot.alert.application.port.out.PriceAlertRepositoryPort;
import com.kjw.tradepilot.alert.domain.PriceAlert;
import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class PriceAlertService implements PriceAlertUseCase {
    private static final Logger log = LoggerFactory.getLogger(PriceAlertService.class);
    private static final long MAX_ACTIVE_ALERTS = 30L;

    private final PriceAlertRepositoryPort repository;
    private final InstrumentRepositoryPort instrumentRepository;
    private final PriceAlertEventPublisherPort eventPublisher;
    private final Clock clock;

    @Autowired
    public PriceAlertService(
            PriceAlertRepositoryPort repository,
            InstrumentRepositoryPort instrumentRepository,
            PriceAlertEventPublisherPort eventPublisher
    ) {
        this(repository, instrumentRepository, eventPublisher, Clock.systemUTC());
    }

    PriceAlertService(
            PriceAlertRepositoryPort repository,
            InstrumentRepositoryPort instrumentRepository,
            PriceAlertEventPublisherPort eventPublisher,
            Clock clock
    ) {
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Mono<PriceAlert> create(CreatePriceAlertCommand command) {
        ValidatedCommand validated = validate(command);
        return requireAccount(validated.accountId())
                .then(ensureCapacity(validated.accountId()))
                .then(Mono.defer(() -> instrumentRepository.find(validated.market(), validated.symbol())))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("거래 가능한 종목을 찾을 수 없습니다.")))
                .map(instrument -> PriceAlert.active(
                        validated.accountId(), validated.market(), validated.symbol(), instrument.name(),
                        validated.condition(), validated.targetPrice(), clock.instant()
                ))
                .flatMap(alert -> Mono.defer(() -> repository.save(alert)))
                .flatMap(alert -> publish(alert).thenReturn(alert));
    }

    @Override
    public Flux<PriceAlert> getAlerts(String accountId) {
        return repository.findAll(normalizeAccountId(accountId));
    }

    @Override
    public Mono<Void> delete(String accountId, UUID alertId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        if (alertId == null) return Mono.error(new IllegalArgumentException("alertId must not be null"));
        return repository.delete(normalizedAccountId, alertId)
                .flatMap(deleted -> deleted
                        ? Mono.empty()
                        : Mono.error(new PriceAlertNotFoundException("가격 알림을 찾을 수 없습니다.")));
    }

    @Override
    public Mono<PriceAlert> reactivate(String accountId, UUID alertId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        if (alertId == null) return Mono.error(new IllegalArgumentException("alertId must not be null"));
        return requireAccount(normalizedAccountId)
                .then(ensureCapacity(normalizedAccountId))
                .then(Mono.defer(() -> repository.reactivate(normalizedAccountId, alertId, clock.instant())))
                .switchIfEmpty(Mono.error(new PriceAlertStateException("트리거된 가격 알림만 다시 활성화할 수 있습니다.")))
                .flatMap(alert -> publish(alert).thenReturn(alert));
    }

    @Override
    public Flux<PriceAlert> stream(String accountId) {
        return eventPublisher.stream(normalizeAccountId(accountId));
    }

    public Mono<Void> evaluate(MarketTick tick) {
        if (tick == null) return Mono.error(new IllegalArgumentException("tick must not be null"));
        return repository.findActive(tick.market(), tick.symbol())
                .filter(alert -> alert.isTriggeredBy(tick.price()))
                .concatMap(alert -> repository.trigger(alert.alertId(), tick.price(), clock.instant())
                        .flatMap(triggered -> publish(triggered).thenReturn(triggered))
                        .onErrorResume(error -> {
                            log.warn("Unable to trigger price alert: alertId={}", alert.alertId(), error);
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<Void> requireAccount(String accountId) {
        return repository.accountExists(accountId)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : Mono.error(new IllegalArgumentException("계좌를 찾을 수 없습니다.")));
    }

    private Mono<Void> ensureCapacity(String accountId) {
        return repository.countActive(accountId)
                .flatMap(count -> count < MAX_ACTIVE_ALERTS
                        ? Mono.empty()
                        : Mono.error(new PriceAlertLimitExceededException(
                                "활성 가격 알림은 계정당 최대 " + MAX_ACTIVE_ALERTS + "개까지 등록할 수 있습니다."
                        )));
    }

    private Mono<Void> publish(PriceAlert alert) {
        return eventPublisher.publish(alert).onErrorResume(error -> {
            log.warn("Unable to publish price alert event: alertId={}", alert.alertId(), error);
            return Mono.empty();
        });
    }

    private ValidatedCommand validate(CreatePriceAlertCommand command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        String accountId = normalizeAccountId(command.accountId());
        if (command.market() == null) throw new IllegalArgumentException("market must not be null");
        String symbol = requireText(command.symbol(), "symbol").toUpperCase(Locale.ROOT);
        if (command.condition() == null) throw new IllegalArgumentException("condition must not be null");
        BigDecimal targetPrice = command.targetPrice();
        if (targetPrice == null || targetPrice.signum() <= 0) {
            throw new IllegalArgumentException("targetPrice must be greater than zero");
        }
        return new ValidatedCommand(accountId, command.market(), symbol, command.condition(), targetPrice);
    }

    private String normalizeAccountId(String value) {
        return requireText(value, "accountId");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private record ValidatedCommand(
            String accountId,
            Market market,
            String symbol,
            PriceAlertCondition condition,
            BigDecimal targetPrice
    ) {
    }
}
