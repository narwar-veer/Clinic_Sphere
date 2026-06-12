package com.clinic.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    RETRY
}