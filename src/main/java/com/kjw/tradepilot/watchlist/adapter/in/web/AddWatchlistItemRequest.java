package com.kjw.tradepilot.watchlist.adapter.in.web;

import com.kjw.tradepilot.marketdata.domain.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record AddWatchlistItemRequest(
        @NotBlank @Size(max = 20) String symbol,
        @NotNull Market market,
        @NotBlank @Size(max = 100) String name
) {
}
