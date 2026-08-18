package com.kjw.tradepilot.order.application.service;

import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.application.port.in.MarketDataQuery;
import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import com.kjw.tradepilot.order.application.OrderRejectedException;
import com.kjw.tradepilot.order.application.port.in.PlaceOrderCommand;
import com.kjw.tradepilot.order.application.port.out.OrderEventPublisherPort;
import com.kjw.tradepilot.order.application.port.out.PaperOrderRepositoryPort;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderStatus;
import com.kjw.tradepilot.order.domain.OrderType;
import com.kjw.tradepilot.order.domain.TradeOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperOrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private final PaperOrderRepositoryPort repository = mock(PaperOrderRepositoryPort.class);
    private final InstrumentRepositoryPort instrumentRepository = mock(InstrumentRepositoryPort.class);
    private final MarketDataQuery marketDataQuery = mock(MarketDataQuery.class);
    private final OrderEventPublisherPort eventPublisher = mock(OrderEventPublisherPort.class);
    private PaperOrderService service;

    @BeforeEach
    void setUp() {
        service = new PaperOrderService(
                repository, instrumentRepository, marketDataQuery, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    @Test
    void fillsMarketBuyImmediatelyAtLatestPrice() {
        arrangeInstrumentAndPrice(new BigDecimal("80000"));
        when(repository.findByIdempotencyKey("local-account", "market-buy-1")).thenReturn(Mono.empty());
        when(repository.findCashBalance("local-account")).thenReturn(Mono.just(new BigDecimal("1000000")));
        when(repository.sumPendingBuyCommitment("local-account")).thenReturn(Mono.just(BigDecimal.ZERO));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(repository.settle(any(), any(), eq("삼성전자"))).thenAnswer(invocation -> {
            TradeOrder order = invocation.getArgument(0);
            return Mono.just(filled(order, new BigDecimal("80000")));
        });

        StepVerifier.create(service.place(command(OrderSide.BUY, OrderType.MARKET, 2, null, "market-buy-1")))
                .assertNext(order -> {
                    assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
                    assertThat(order.averageFillPrice()).isEqualByComparingTo("80000");
                })
                .verifyComplete();
    }

    @Test
    void leavesLimitBuyPendingUntilPriceMatches() {
        arrangeInstrumentAndPrice(new BigDecimal("80000"));
        when(repository.findByIdempotencyKey("local-account", "limit-buy-1")).thenReturn(Mono.empty());
        when(repository.findCashBalance("local-account")).thenReturn(Mono.just(new BigDecimal("1000000")));
        when(repository.sumPendingBuyCommitment("local-account")).thenReturn(Mono.just(BigDecimal.ZERO));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.place(command(
                        OrderSide.BUY, OrderType.LIMIT, 2, new BigDecimal("79000"), "limit-buy-1")))
                .assertNext(order -> assertThat(order.status()).isEqualTo(OrderStatus.PENDING))
                .verifyComplete();

        verify(repository, never()).settle(any(), any(), any());
    }

    @Test
    void rejectsBuyWhenAvailableCashIsInsufficient() {
        arrangeInstrumentAndPrice(new BigDecimal("80000"));
        when(repository.findByIdempotencyKey("local-account", "buy-too-large")).thenReturn(Mono.empty());
        when(repository.findCashBalance("local-account")).thenReturn(Mono.just(new BigDecimal("100000")));
        when(repository.sumPendingBuyCommitment("local-account")).thenReturn(Mono.just(BigDecimal.ZERO));

        StepVerifier.create(service.place(command(OrderSide.BUY, OrderType.MARKET, 2, null, "buy-too-large")))
                .expectError(OrderRejectedException.class)
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsSellWhenPendingOrdersReserveHoldings() {
        arrangeInstrumentAndPrice(new BigDecimal("80000"));
        when(repository.findByIdempotencyKey("local-account", "sell-too-large")).thenReturn(Mono.empty());
        when(repository.findPositionQuantity("local-account", Market.KRX, "005930")).thenReturn(Mono.just(5L));
        when(repository.sumPendingSellQuantity("local-account", Market.KRX, "005930")).thenReturn(Mono.just(3L));

        StepVerifier.create(service.place(command(OrderSide.SELL, OrderType.MARKET, 3, null, "sell-too-large")))
                .expectError(OrderRejectedException.class)
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void matchesPendingLimitOrderOnCrossingTick() {
        TradeOrder pending = TradeOrder.pending(
                "local-account", Market.KRX, "005930", "삼성전자", OrderSide.BUY,
                OrderType.LIMIT, 1, new BigDecimal("79000"), "pending-1", NOW
        );
        when(repository.findPending(Market.KRX, "005930")).thenReturn(Flux.just(pending));
        when(repository.settle(eq(pending), any(), eq("삼성전자")))
                .thenReturn(Mono.just(filled(pending, new BigDecimal("78900"))));

        StepVerifier.create(service.match(tick(new BigDecimal("78900"))))
                .verifyComplete();

        verify(repository).settle(eq(pending), any(), eq("삼성전자"));
    }

    private void arrangeInstrumentAndPrice(BigDecimal price) {
        when(instrumentRepository.find(Market.KRX, "005930")).thenReturn(Mono.just(new SecurityInstrument(
                Market.KRX, "005930", "삼성전자", "KRX", "KRW", NOW
        )));
        when(marketDataQuery.getLatest(Market.KRX, "005930")).thenReturn(Mono.just(tick(price)));
    }

    private PlaceOrderCommand command(
            OrderSide side, OrderType type, long quantity, BigDecimal limitPrice, String idempotencyKey
    ) {
        return new PlaceOrderCommand(
                "local-account", Market.KRX, "005930", side, type, quantity, limitPrice, idempotencyKey
        );
    }

    private MarketTick tick(BigDecimal price) {
        return new MarketTick("005930", Market.KRX, price, 100, NOW, NOW, "TEST");
    }

    private TradeOrder filled(TradeOrder order, BigDecimal price) {
        return new TradeOrder(
                order.orderId(), order.accountId(), order.market(), order.symbol(), order.name(), order.side(),
                order.orderType(), order.quantity(), order.limitPrice(), OrderStatus.FILLED, order.quantity(), price,
                order.idempotencyKey(), order.createdAt(), NOW
        );
    }
}
