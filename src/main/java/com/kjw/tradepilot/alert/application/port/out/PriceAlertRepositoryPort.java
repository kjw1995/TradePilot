package com.kjw.tradepilot.alert.application.port.out;

import com.kjw.tradepilot.alert.domain.PriceAlert;
import com.kjw.tradepilot.marketdata.domain.Market;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PriceAlertRepositoryPort {
    Mono<Boolean> accountExists(String accountId);

    Mono<Long> countActive(String accountId);

    Mono<PriceAlert> save(PriceAlert alert);

    Flux<PriceAlert> findAll(String accountId);

    Flux<PriceAlert> findActive(Market market, String symbol);

    Mono<Boolean> delete(String accountId, UUID alertId);

    Mono<PriceAlert> reactivate(String accountId, UUID alertId, Instant updatedAt);

    Mono<PriceAlert> trigger(UUID alertId, BigDecimal triggeredPrice, Instant triggeredAt);
}
