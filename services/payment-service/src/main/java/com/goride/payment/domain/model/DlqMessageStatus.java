package com.goride.payment.domain.model;

public enum DlqMessageStatus {
    DEAD,
    REPROCESSED,
    RESOLVED,
    DISCARDED
}
