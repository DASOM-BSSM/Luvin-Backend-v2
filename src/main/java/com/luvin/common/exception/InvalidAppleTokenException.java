package com.luvin.common.exception;

public class InvalidAppleTokenException extends RuntimeException {
    public InvalidAppleTokenException(String message) {
        super(message);
    }
}
