package com.kjw.tradepilot.order.application;

public class OrderNotCancelableException extends RuntimeException {
    public OrderNotCancelableException(String message) {
        super(message);
    }
}
