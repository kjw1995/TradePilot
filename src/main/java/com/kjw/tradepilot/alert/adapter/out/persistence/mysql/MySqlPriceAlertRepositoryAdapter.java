package com.kjw.tradepilot.alert.adapter.out.persistence.mysql;

import com.kjw.tradepilot.alert.application.port.out.PriceAlertRepositoryPort;
import com.kjw.tradepilot.alert.domain.PriceAlert;
import com.kjw.tradepilot.alert.domain.PriceAlertCondition;
import com.kjw.tradepilot.alert.domain.PriceAlertStatus;
import com.kjw.tradepilot.marketdata.domain.Market;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
class MySqlPriceAlertRepositoryAdapter implements PriceAlertRepositoryPort {
    private final DatabaseClient databaseClient;

    MySqlPriceAlertRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> accountExists(String accountId) {
        return databaseClient.sql("SELECT COUNT(*) AS count FROM portfolio_accounts WHERE account_id = :accountId")
                .bind("accountId", accountId)
                .map((row, metadata) -> row.get("count", Long.class) > 0)
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> countActive(String accountId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS count
                        FROM price_alerts
                        WHERE account_id = :accountId AND status = 'ACTIVE'
                        """)
                .bind("accountId", accountId)
                .map((row, metadata) -> row.get("count", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<PriceAlert> save(PriceAlert alert) {
        return databaseClient.sql("""
                        INSERT INTO price_alerts (
                            alert_id, account_id, market, symbol, name, condition_type, target_price,
                            status, last_triggered_price, last_triggered_at, created_at, updated_at
                        ) VALUES (
                            :alertId, :accountId, :market, :symbol, :name, :conditionType, :targetPrice,
                            :status, :lastTriggeredPrice, :lastTriggeredAt, :createdAt, :updatedAt
                        )
                        """)
                .bind("alertId", alert.alertId().toString())
                .bind("accountId", alert.accountId())
                .bind("market", alert.market().name())
                .bind("symbol", alert.symbol())
                .bind("name", alert.name())
                .bind("conditionType", alert.condition().name())
                .bind("targetPrice", alert.targetPrice())
                .bind("status", alert.status().name())
                .bindNull("lastTriggeredPrice", BigDecimal.class)
                .bindNull("lastTriggeredAt", LocalDateTime.class)
                .bind("createdAt", toDateTime(alert.createdAt()))
                .bind("updatedAt", toDateTime(alert.updatedAt()))
                .fetch().rowsUpdated()
                .thenReturn(alert);
    }

    @Override
    public Flux<PriceAlert> findAll(String accountId) {
        return alertQuery("WHERE account_id = :accountId ORDER BY created_at DESC")
                .bind("accountId", accountId)
                .map((row, metadata) -> mapAlert(row))
                .all();
    }

    @Override
    public Flux<PriceAlert> findActive(Market market, String symbol) {
        return alertQuery("WHERE status = 'ACTIVE' AND market = :market AND symbol = :symbol ORDER BY created_at ASC")
                .bind("market", market.name())
                .bind("symbol", symbol)
                .map((row, metadata) -> mapAlert(row))
                .all();
    }

    @Override
    public Mono<Boolean> delete(String accountId, UUID alertId) {
        return databaseClient.sql("DELETE FROM price_alerts WHERE account_id = :accountId AND alert_id = :alertId")
                .bind("accountId", accountId)
                .bind("alertId", alertId.toString())
                .fetch().rowsUpdated()
                .map(updated -> updated == 1);
    }

    @Override
    public Mono<PriceAlert> reactivate(String accountId, UUID alertId, Instant updatedAt) {
        return databaseClient.sql("""
                        UPDATE price_alerts
                        SET status = 'ACTIVE', last_triggered_price = NULL, last_triggered_at = NULL,
                            updated_at = :updatedAt
                        WHERE account_id = :accountId AND alert_id = :alertId AND status = 'TRIGGERED'
                        """)
                .bind("updatedAt", toDateTime(updatedAt))
                .bind("accountId", accountId)
                .bind("alertId", alertId.toString())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1 ? find(accountId, alertId) : Mono.empty());
    }

    @Override
    public Mono<PriceAlert> trigger(UUID alertId, BigDecimal triggeredPrice, Instant triggeredAt) {
        return databaseClient.sql("""
                        UPDATE price_alerts
                        SET status = 'TRIGGERED', last_triggered_price = :triggeredPrice,
                            last_triggered_at = :triggeredAt, updated_at = :triggeredAt
                        WHERE alert_id = :alertId AND status = 'ACTIVE'
                        """)
                .bind("triggeredPrice", triggeredPrice)
                .bind("triggeredAt", toDateTime(triggeredAt))
                .bind("alertId", alertId.toString())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 1 ? find(alertId) : Mono.empty());
    }

    private Mono<PriceAlert> find(String accountId, UUID alertId) {
        return alertQuery("WHERE account_id = :accountId AND alert_id = :alertId")
                .bind("accountId", accountId)
                .bind("alertId", alertId.toString())
                .map((row, metadata) -> mapAlert(row))
                .one();
    }

    private Mono<PriceAlert> find(UUID alertId) {
        return alertQuery("WHERE alert_id = :alertId")
                .bind("alertId", alertId.toString())
                .map((row, metadata) -> mapAlert(row))
                .one();
    }

    private DatabaseClient.GenericExecuteSpec alertQuery(String suffix) {
        return databaseClient.sql("""
                SELECT alert_id, account_id, market, symbol, name, condition_type, target_price,
                       status, last_triggered_price, last_triggered_at, created_at, updated_at
                FROM price_alerts
                """ + suffix);
    }

    private PriceAlert mapAlert(Row row) {
        LocalDateTime lastTriggeredAt = row.get("last_triggered_at", LocalDateTime.class);
        return new PriceAlert(
                UUID.fromString(row.get("alert_id", String.class)),
                row.get("account_id", String.class),
                Market.valueOf(row.get("market", String.class)),
                row.get("symbol", String.class),
                row.get("name", String.class),
                PriceAlertCondition.valueOf(row.get("condition_type", String.class)),
                row.get("target_price", BigDecimal.class),
                PriceAlertStatus.valueOf(row.get("status", String.class)),
                row.get("last_triggered_price", BigDecimal.class),
                lastTriggeredAt == null ? null : lastTriggeredAt.toInstant(ZoneOffset.UTC),
                row.get("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                row.get("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    private LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
