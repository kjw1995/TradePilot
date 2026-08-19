package com.kjw.tradepilot.alert.application;

public class PriceAlertNotFoundException extends RuntimeException {
    public PriceAlertNotFoundException(String message) {
        super(message);
    }
}
