package com.goride.notification.domain.model;

public enum DlqMessageStatus {
    DEAD,
    REPROCESSED,
    RESOLVED,
    DISCARDED
}
