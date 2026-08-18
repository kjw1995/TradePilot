package com.kjw.tradepilot.order.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.order.application.OrderRejectedException;
import com.kjw.tradepilot.order.application.port.out.PaperOrderRepositoryPort;
import com.kjw.tradepilot.order.domain.OrderSide;
import com.kjw.tradepilot.order.domain.OrderStatus;
import com.kjw.tradepilot.order.domain.OrderType;
import com.kjw.tradepilot.order.domain.TradeExecution;
import com.kjw.tradepilot.order.domain.TradeOrder;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
class MySqlPaperOrderRepositoryAdapter implements PaperOrderRepositoryPort {
    private final DatabaseClient databaseClient;

    MySqlPaperOrderRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<TradeOrder> save(TradeOrder order) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO trade_orders (
                            order_id, account_id, market, symbol, name, side, order_type, quantity,
                            limit_price, status, filled_quantity, average_fill_price, idempotency_key,
                            created_at, updated_at
                        ) VALUES (
                            :orderId, :accountId, :market, :symbol, :name, :side, :orderType, :quantity,
                            :limitPrice, :status, :filledQuantity, :averageFillPrice, :idempotencyKey,
                            :createdAt, :updatedAt
                        )
                        """)
                .bind("orderId", order.orderId().toString())
                .bind("accountId", order.accountId())
                .bind("market", order.market().name())
                .bind("symbol", order.symbol())
                .bind("name", order.name())
                .bind("side", order.side().name())
                .bind("orderType", order.orderType().name())
                .bind("quantity", order.quantity())
                .bind("status", order.status().name())
                .bind("filledQuantity", order.filledQuantity())
                .bind("idempotencyKey", order.idempotencyKey())
                .bind("createdAt", LocalDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC))
                .bind("updatedAt", LocalDateTime.ofInstant(order.updatedAt(), ZoneOffset.UTC));
        spec = order.limitPrice() == null
                ? spec.bindNull("limitPrice", BigDecimal.class)
                : spec.bind("limitPrice", order.limitPrice());
        spec = order.averageFillPrice() == null
                ? spec.bindNull("averageFillPrice", BigDecimal.class)
                : spec.bind("averageFillPrice", order.averageFillPrice());
        return spec.fetch().rowsUpdated().thenReturn(order);
    }

    @Override
    public Mono<TradeOrder> find(UUID orderId) {
        return orderQuery("WHERE order_id = :orderId")
                .bind("orderId", orderId.toString())
                .map((row, metadata) -> mapOrder(row))
                .one();
    }

    @Override
    public Mono<TradeOrder> findByIdempotencyKey(String accountId, String idempotencyKey) {
        return orderQuery("WHERE account_id = :accountId AND idempotency_key = :idempotencyKey")
                .bind("accountId", accountId)
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> mapOrder(row))
                .one();
    }

    @Override
    public Flux<TradeOrder> findAll(String accountId) {
        return orderQuery("WHERE account_id = :accountId ORDER BY created_at DESC LIMIT 100")
                .bind("accountId", accountId)
                .map((row, metadata) -> mapOrder(row))
                .all();
    }

    @Override
    public Flux<TradeOrder> findPending(Market market, String symbol) {
        return orderQuery("WHERE status = 'PENDING' AND market = :market AND symbol = :symbol ORDER BY created_at ASC")
                .bind("market", market.name())
                .bind("symbol", symbol)
                .map((row, metadata) -> mapOrder(row))
                .all();
    }

    @Override
    public Flux<TradeExecution> findExecutions(String accountId) {
        return databaseClient.sql("""
                        SELECT execution_id, order_id, account_id, market, symbol, side, quantity, price, executed_at
                        FROM trade_executions
                        WHERE account_id = :accountId
                        ORDER BY executed_at DESC
                        LIMIT 100
                        """)
                .bind("accountId", accountId)
                .map((row, metadata) -> new TradeExecution(
                        UUID.fromString(row.get("execution_id", String.class)),
                        UUID.fromString(row.get("order_id", String.class)),
                        row.get("account_id", String.class),
                        Market.valueOf(row.get("market", String.class)),
                        row.get("symbol", String.class),
                        OrderSide.valueOf(row.get("side", String.class)),
                        row.get("quantity", Long.class),
                        row.get("price", BigDecimal.class),
                        row.get("executed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
                ))
                .all();
    }

    @Override
    public Mono<BigDecimal> findCashBalance(String accountId) {
        return databaseClient.sql("SELECT cash_balance FROM portfolio_accounts WHERE account_id = :accountId")
                .bind("accountId", accountId)
                .map((row, metadata) -> row.get("cash_balance", BigDecimal.class))
                .one();
    }

    @Override
    public Mono<Long> findPositionQuantity(String accountId, Market market, String symbol) {
        return databaseClient.sql("""
                        SELECT quantity FROM portfolio_positions
                        WHERE account_id = :accountId AND market = :market AND symbol = :symbol
                        """)
                .bind("accountId", accountId)
                .bind("market", market.name())
                .bind("symbol", symbol)
                .map((row, metadata) -> row.get("quantity", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<BigDecimal> sumPendingBuyCommitment(String accountId) {
        return databaseClient.sql("""
                        SELECT COALESCE(SUM(limit_price * quantity), 0) AS amount
                        FROM trade_orders
                        WHERE account_id = :accountId AND side = 'BUY' AND status = 'PENDING'
                        """)
                .bind("accountId", accountId)
                .map((row, metadata) -> row.get("amount", BigDecimal.class))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<Long> sumPendingSellQuantity(String accountId, Market market, String symbol) {
        return databaseClient.sql("""
                        SELECT COALESCE(SUM(quantity), 0) AS quantity
                        FROM trade_orders
                        WHERE account_id = :accountId AND market = :market AND symbol = :symbol
                          AND side = 'SELL' AND status = 'PENDING'
                        """)
                .bind("accountId", accountId)
                .bind("market", market.name())
                .bind("symbol", symbol)
                .map((row, metadata) -> row.get("quantity", BigDecimal.class).longValue())
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    @Transactional
    public Mono<TradeOrder> cancel(String accountId, UUID orderId, Instant canceledAt) {
        return databaseClient.sql("""
                        UPDATE trade_orders
                        SET status = 'CANCELED', updated_at = :updatedAt
                        WHERE order_id = :orderId AND account_id = :accountId AND status = 'PENDING'
                        """)
                .bind("updatedAt", LocalDateTime.ofInstant(canceledAt, ZoneOffset.UTC))
                .bind("orderId", orderId.toString())
                .bind("accountId", accountId)
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1 ? find(orderId) : Mono.empty());
    }

    @Override
    @Transactional
    public Mono<TradeOrder> settle(TradeOrder order, TradeExecution execution, String instrumentName) {
        return claim(order, execution)
                .flatMap(claimed -> {
                    if (!claimed) return Mono.empty();
                    Mono<Void> portfolioUpdate = order.side() == OrderSide.BUY
                            ? applyBuy(order, execution, instrumentName)
                            : applySell(order, execution);
                    return portfolioUpdate
                            .then(saveExecution(execution))
                            .then(find(order.orderId()));
                });
    }

    private Mono<Boolean> claim(TradeOrder order, TradeExecution execution) {
        return databaseClient.sql("""
                        UPDATE trade_orders
                        SET status = 'FILLED', filled_quantity = quantity,
                            average_fill_price = :price, updated_at = :updatedAt
                        WHERE order_id = :orderId AND status = 'PENDING'
                        """)
                .bind("price", execution.price())
                .bind("updatedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .bind("orderId", order.orderId().toString())
                .fetch().rowsUpdated()
                .map(updated -> updated == 1);
    }

    private Mono<Void> applyBuy(TradeOrder order, TradeExecution execution, String instrumentName) {
        BigDecimal amount = execution.price().multiply(BigDecimal.valueOf(execution.quantity()));
        Mono<Void> debit = databaseClient.sql("""
                        UPDATE portfolio_accounts
                        SET cash_balance = cash_balance - :amount, synced_at = :syncedAt
                        WHERE account_id = :accountId AND cash_balance >= :amount
                        """)
                .bind("amount", amount)
                .bind("syncedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .bind("accountId", order.accountId())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new OrderRejectedException("주문 체결에 필요한 예수금이 부족합니다.")));

        Mono<Void> position = databaseClient.sql("""
                        UPDATE portfolio_positions
                        SET average_price = ((average_price * quantity) + (:price * :quantity)) / (quantity + :quantity),
                            quantity = quantity + :quantity, synced_at = :syncedAt
                        WHERE account_id = :accountId AND market = :market AND symbol = :symbol
                        """)
                .bind("price", execution.price())
                .bind("quantity", execution.quantity())
                .bind("syncedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .bind("accountId", order.accountId())
                .bind("market", order.market().name())
                .bind("symbol", order.symbol())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1 ? Mono.empty() : insertPosition(order, execution, instrumentName));
        return debit.then(position);
    }

    private Mono<Void> insertPosition(TradeOrder order, TradeExecution execution, String instrumentName) {
        return databaseClient.sql("""
                        INSERT INTO portfolio_positions (
                            account_id, symbol, market, name, quantity, average_price, synced_at
                        ) VALUES (:accountId, :symbol, :market, :name, :quantity, :price, :syncedAt)
                        """)
                .bind("accountId", order.accountId())
                .bind("symbol", order.symbol())
                .bind("market", order.market().name())
                .bind("name", instrumentName)
                .bind("quantity", execution.quantity())
                .bind("price", execution.price())
                .bind("syncedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> applySell(TradeOrder order, TradeExecution execution) {
        BigDecimal amount = execution.price().multiply(BigDecimal.valueOf(execution.quantity()));
        Mono<Void> position = databaseClient.sql("""
                        UPDATE portfolio_positions
                        SET quantity = quantity - :quantity, synced_at = :syncedAt
                        WHERE account_id = :accountId AND market = :market AND symbol = :symbol
                          AND quantity >= :quantity
                        """)
                .bind("quantity", execution.quantity())
                .bind("syncedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .bind("accountId", order.accountId())
                .bind("market", order.market().name())
                .bind("symbol", order.symbol())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new OrderRejectedException("주문 체결에 필요한 보유 수량이 부족합니다.")));
        Mono<Void> credit = databaseClient.sql("""
                        UPDATE portfolio_accounts
                        SET cash_balance = cash_balance + :amount, synced_at = :syncedAt
                        WHERE account_id = :accountId
                        """)
                .bind("amount", amount)
                .bind("syncedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .bind("accountId", order.accountId())
                .fetch().rowsUpdated().then();
        Mono<Void> cleanup = databaseClient.sql("""
                        DELETE FROM portfolio_positions
                        WHERE account_id = :accountId AND market = :market AND symbol = :symbol AND quantity = 0
                        """)
                .bind("accountId", order.accountId())
                .bind("market", order.market().name())
                .bind("symbol", order.symbol())
                .fetch().rowsUpdated().then();
        return position.then(credit).then(cleanup);
    }

    private Mono<Void> saveExecution(TradeExecution execution) {
        return databaseClient.sql("""
                        INSERT INTO trade_executions (
                            execution_id, order_id, account_id, market, symbol, side, quantity, price, executed_at
                        ) VALUES (
                            :executionId, :orderId, :accountId, :market, :symbol, :side, :quantity, :price, :executedAt
                        )
                        """)
                .bind("executionId", execution.executionId().toString())
                .bind("orderId", execution.orderId().toString())
                .bind("accountId", execution.accountId())
                .bind("market", execution.market().name())
                .bind("symbol", execution.symbol())
                .bind("side", execution.side().name())
                .bind("quantity", execution.quantity())
                .bind("price", execution.price())
                .bind("executedAt", LocalDateTime.ofInstant(execution.executedAt(), ZoneOffset.UTC))
                .fetch().rowsUpdated().then();
    }

    private DatabaseClient.GenericExecuteSpec orderQuery(String suffix) {
        return databaseClient.sql("""
                SELECT order_id, account_id, market, symbol, name, side, order_type, quantity,
                       limit_price, status, filled_quantity, average_fill_price, idempotency_key,
                       created_at, updated_at
                FROM trade_orders
                """ + suffix);
    }

    private TradeOrder mapOrder(Row row) {
        return new TradeOrder(
                UUID.fromString(row.get("order_id", String.class)),
                row.get("account_id", String.class),
                Market.valueOf(row.get("market", String.class)),
                row.get("symbol", String.class),
                row.get("name", String.class),
                OrderSide.valueOf(row.get("side", String.class)),
                OrderType.valueOf(row.get("order_type", String.class)),
                row.get("quantity", Long.class),
                row.get("limit_price", BigDecimal.class),
                OrderStatus.valueOf(row.get("status", String.class)),
                row.get("filled_quantity", Long.class),
                row.get("average_fill_price", BigDecimal.class),
                row.get("idempotency_key", String.class),
                row.get("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                row.get("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
