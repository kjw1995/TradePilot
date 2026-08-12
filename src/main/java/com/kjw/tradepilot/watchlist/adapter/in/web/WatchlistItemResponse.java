package com.kjw.tradepilot.watchlist.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.domain.WatchlistItem;

import java.time.Instant;

record WatchlistItemResponse(
        String accountId,
        String symbol,
        Market market,
        String name,
        int displayOrder,
        Instant createdAt
) {
    static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
                item.accountId(),
                item.symbol(),
                item.market(),
                item.name(),
                item.displayOrder(),
                item.createdAt()
        );
    }
}
