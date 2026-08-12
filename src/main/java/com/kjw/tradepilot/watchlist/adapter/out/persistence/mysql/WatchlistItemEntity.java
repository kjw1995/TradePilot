package com.kjw.tradepilot.watchlist.adapter.out.persistence.mysql;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Table("watchlist_items")
record WatchlistItemEntity(
        @Id Long id,
        String accountId,
        String symbol,
        String market,
        String name,
        int displayOrder,
        LocalDateTime createdAt
) {
    static WatchlistItemEntity from(WatchlistItem item) {
        return new WatchlistItemEntity(
                item.id(),
                item.accountId(),
                item.symbol(),
                item.market().name(),
                item.name(),
                item.displayOrder(),
                LocalDateTime.ofInstant(item.createdAt(), ZoneOffset.UTC)
        );
    }

    WatchlistItem toDomain() {
        return new WatchlistItem(
                id,
                accountId,
                symbol,
                Market.valueOf(market),
                name,
                displayOrder,
                createdAt.toInstant(ZoneOffset.UTC)
        );
    }
}
