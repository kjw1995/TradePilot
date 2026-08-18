package com.kjw.tradepilot.order.application.port.in;

import com.kjw.tradepilot.order.domain.TradeExecution;
import com.kjw.tradepilot.order.domain.TradeOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaperOrderUseCase {
    Mono<TradeOrder> place(PlaceOrderCommand command);

    Flux<TradeOrder> getOrders(String accountId);

    Flux<TradeExecution> getExecutions(String accountId);

    Mono<TradeOrder> cancel(String accountId, UUID orderId);

    Flux<TradeOrder> stream(String accountId);
}
