package com.kjw.tradepilot.alert.application;

public class PriceAlertLimitExceededException extends RuntimeException {
    public PriceAlertLimitExceededException(String message) {
        super(message);
    }
}
