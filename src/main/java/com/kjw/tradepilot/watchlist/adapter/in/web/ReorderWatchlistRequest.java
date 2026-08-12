package com.kjw.tradepilot.watchlist.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.watchlist.domain.WatchlistKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

record ReorderWatchlistRequest(
        @NotEmpty @Size(max = 30) List<@Valid Item> items
) {
    List<WatchlistKey> toKeys() {
        return items.stream().map(item -> new WatchlistKey(item.market(), item.symbol())).toList();
    }

    record Item(
            @NotNull Market market,
            @NotBlank @Size(max = 20) String symbol
    ) {
    }
}
