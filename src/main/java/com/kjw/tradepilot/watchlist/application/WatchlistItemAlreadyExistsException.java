package com.kjw.tradepilot.watchlist.application;

public class WatchlistItemAlreadyExistsException extends RuntimeException {
    public WatchlistItemAlreadyExistsException(String symbol) {
        super("Watchlist item already exists: " + symbol);
    }
}
