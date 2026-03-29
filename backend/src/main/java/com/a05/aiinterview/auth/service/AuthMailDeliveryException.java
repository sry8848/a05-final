package com.a05.aiinterview.auth.service;

public class AuthMailDeliveryException extends RuntimeException {

    public AuthMailDeliveryException(String message) {
        super(message);
    }

    public AuthMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
