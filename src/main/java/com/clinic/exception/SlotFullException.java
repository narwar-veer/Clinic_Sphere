package com.clinic.exception;

public class SlotFullException extends RuntimeException {
    public SlotFullException(String message) {
        super(message);
    }
}
