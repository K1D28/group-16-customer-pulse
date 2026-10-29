package com.customerpulse.service;

/** A rule was broken; the message is shown to the user. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
