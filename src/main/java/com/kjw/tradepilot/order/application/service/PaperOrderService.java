package com.kjw.tradepilot.order.application.service;

import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import com.kjw.tradepilot.order.application.OrderNotCancelableException;
import com.kjw.tradepilot.order.application.OrderRejectedException;
import com.kjw.tradepilot.order.application.port.in.PaperOrderUseCase;
import com.kjw.tradepilot.order.application.port.in.PlaceOrderCommand;
import com.kjw.tradepilot.order.application.port.out.OrderEventPublisherPort;
import com.kjw.tradepilot.order.application.port.out.PaperOrderRepositoryPort;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderType;
import com.kjw.tradepilot.order.domain.TradeExecution;
import com.kjw.tradepilot.order.domain.TradeOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaperOrderService implements PaperOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(PaperOrderService.class);
    private static final long MAX_ORDER_QUANTITY = 1_000_000L;

    private final PaperOrderRepositoryPort repository;
    private final InstrumentRepositoryPort instrumentRepository;
    private final MarketDataQuery marketDataQuery;
    private final OrderEventPublisherPort eventPublisher;
    private final Clock clock;

    @Autowired
    public PaperOrderService(
            PaperOrderRepositoryPort repository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataQuery marketDataQuery,
            OrderEventPublisherPort eventPublisher
    ) {
        this(repository, instrumentRepository, marketDataQuery, eventPublisher, Clock.systemUTC());
    }

    PaperOrderService(
            PaperOrderRepositoryPort repository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataQuery marketDataQuery,
            OrderEventPublisherPort eventPublisher,
            Clock clock
    ) {
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataQuery = marketDataQuery;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Mono<TradeOrder> place(PlaceOrderCommand command) {
        ValidatedCommand validated = validate(command);
        return repository.findByIdempotencyKey(validated.accountId(), validated.idempotencyKey())
                .switchIfEmpty(Mono.defer(() -> instrumentRepository.find(validated.market(), validated.symbol())
                        .switchIfEmpty(Mono.error(new OrderRejectedException("거래 가능한 종목을 찾을 수 없습니다.")))
                        .flatMap(instrument -> marketDataQuery.getLatest(validated.market(), validated.symbol())
                                .map(tick -> Optional.of(tick.price()))
                                .defaultIfEmpty(Optional.empty())
                                .flatMap(latestPrice -> createOrder(validated, instrument, latestPrice)))));
    }

    @Override
    public Flux<TradeOrder> getOrders(String accountId) {
        return repository.findAll(normalizeAccountId(accountId));
    }

    @Override
    public Flux<TradeExecution> getExecutions(String accountId) {
        return repository.findExecutions(normalizeAccountId(accountId));
    }

    @Override
    public Mono<TradeOrder> cancel(String accountId, UUID orderId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        if (orderId == null) return Mono.error(new IllegalArgumentException("orderId must not be null"));
        return repository.cancel(normalizedAccountId, orderId, clock.instant())
                .switchIfEmpty(Mono.error(new OrderNotCancelableException("대기 중인 주문만 취소할 수 있습니다.")))
                .flatMap(order -> publish(order).thenReturn(order));
    }

    @Override
    public Flux<TradeOrder> stream(String accountId) {
        return eventPublisher.stream(normalizeAccountId(accountId));
    }

    public Mono<Void> match(MarketTick tick) {
        return repository.findPending(tick.market(), tick.symbol())
                .filter(order -> order.canExecuteAt(tick.price()))
                .concatMap(order -> execute(order, tick.price()).onErrorResume(error -> {
                    log.warn("Unable to settle paper order: orderId={}", order.orderId(), error);
                    return Mono.empty();
                }))
                .then();
    }

    private Mono<TradeOrder> createOrder(
            ValidatedCommand command,
            SecurityInstrument instrument,
            Optional<BigDecimal> latestPrice
    ) {
        if (command.orderType() == OrderType.MARKET && latestPrice.isEmpty()) {
            return Mono.error(new OrderRejectedException("시장가 주문에 필요한 최신 시세가 없습니다."));
        }
        TradeOrder order = TradeOrder.pending(
                command.accountId(), command.market(), command.symbol(), instrument.name(), command.side(),
                command.orderType(), command.quantity(), command.limitPrice(), command.idempotencyKey(), clock.instant()
        );
        return validateResources(order, latestPrice.orElse(null))
                .then(Mono.defer(() -> repository.save(order)))
                .flatMap(saved -> publish(saved).thenReturn(saved))
                .flatMap(saved -> latestPrice.filter(saved::canExecuteAt)
                        .map(price -> execute(saved, price))
                        .orElseGet(() -> Mono.just(saved)));
    }

    private Mono<Void> validateResources(TradeOrder order, BigDecimal latestPrice) {
        if (order.side() == OrderSide.BUY) {
            BigDecimal required = order.reservationPrice(latestPrice).multiply(BigDecimal.valueOf(order.quantity()));
            return Mono.zip(
                            repository.findCashBalance(order.accountId())
                                    .switchIfEmpty(Mono.error(new OrderRejectedException("계좌를 찾을 수 없습니다."))),
                            repository.sumPendingBuyCommitment(order.accountId())
                    )
                    .flatMap(values -> values.getT1().subtract(values.getT2()).compareTo(required) >= 0
                            ? Mono.empty()
                            : Mono.error(new OrderRejectedException("주문 가능 예수금이 부족합니다.")));
        }
        return Mono.zip(
                        repository.findPositionQuantity(order.accountId(), order.market(), order.symbol()),
                        repository.sumPendingSellQuantity(order.accountId(), order.market(), order.symbol())
                )
                .flatMap(values -> values.getT1() - values.getT2() >= order.quantity()
                        ? Mono.empty()
                        : Mono.error(new OrderRejectedException("주문 가능한 보유 수량이 부족합니다.")));
    }

    private Mono<TradeOrder> execute(TradeOrder order, BigDecimal price) {
        TradeExecution execution = TradeExecution.from(order, price, clock.instant());
        return repository.settle(order, execution, order.name())
                .flatMap(filled -> publish(filled).thenReturn(filled));
    }

    private Mono<Void> publish(TradeOrder order) {
        return eventPublisher.publish(order).onErrorResume(error -> {
            log.warn("Unable to publish paper order event: orderId={}", order.orderId(), error);
            return Mono.empty();
        });
    }

    private ValidatedCommand validate(PlaceOrderCommand command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        String accountId = normalizeAccountId(command.accountId());
        if (command.market() == null) throw new IllegalArgumentException("market must not be null");
        String symbol = normalizeSymbol(command.symbol());
        if (command.side() == null) throw new IllegalArgumentException("side must not be null");
        if (command.orderType() == null) throw new IllegalArgumentException("orderType must not be null");
        if (command.quantity() <= 0 || command.quantity() > MAX_ORDER_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_ORDER_QUANTITY);
        }
        BigDecimal limitPrice = command.limitPrice();
        if (command.orderType() == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("limitPrice must be greater than zero for LIMIT order");
        }
        if (command.orderType() == OrderType.MARKET) limitPrice = null;
        String idempotencyKey = requireText(command.idempotencyKey(), "idempotencyKey");
        if (idempotencyKey.length() > 100) throw new IllegalArgumentException("idempotencyKey must be at most 100 characters");
        return new ValidatedCommand(
                accountId, command.market(), symbol, command.side(), command.orderType(),
                command.quantity(), limitPrice, idempotencyKey
        );
    }

    private String normalizeAccountId(String value) {
        return requireText(value, "accountId");
    }

    private String normalizeSymbol(String value) {
        return requireText(value, "symbol").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private record ValidatedCommand(
            String accountId,
            com.kjw.tradepilot.marketdata.domain.Market market,
            String symbol,
            OrderSide side,
            OrderType orderType,
            long quantity,
            BigDecimal limitPrice,
            String idempotencyKey
    ) {
    }
}
