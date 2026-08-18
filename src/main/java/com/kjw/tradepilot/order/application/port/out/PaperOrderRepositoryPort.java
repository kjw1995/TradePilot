package com.kjw.tradepilot.order.application.port.out;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.TradeExecution;
import com.kjw.tradepilot.order.domain.TradeOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PaperOrderRepositoryPort {
    Mono<TradeOrder> save(TradeOrder order);

    Mono<TradeOrder> find(UUID orderId);

    Mono<TradeOrder> findByIdempotencyKey(String accountId, String idempotencyKey);

    Flux<TradeOrder> findAll(String accountId);

    Flux<TradeOrder> findPending(Market market, String symbol);

    Flux<TradeExecution> findExecutions(String accountId);

    Mono<BigDecimal> findCashBalance(String accountId);

    Mono<Long> findPositionQuantity(String accountId, Market market, String symbol);

    Mono<BigDecimal> sumPendingBuyCommitment(String accountId);

    Mono<Long> sumPendingSellQuantity(String accountId, Market market, String symbol);

    Mono<TradeOrder> cancel(String accountId, UUID orderId, Instant canceledAt);

    Mono<TradeOrder> settle(TradeOrder order, TradeExecution execution, String instrumentName);
}
