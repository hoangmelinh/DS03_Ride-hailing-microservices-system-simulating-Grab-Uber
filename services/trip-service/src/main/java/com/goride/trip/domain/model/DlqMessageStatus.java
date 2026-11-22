package com.goride.trip.domain.model;

public enum DlqMessageStatus {
    DEAD,
    REPROCESSED,
    RESOLVED,
    DISCARDED
}
