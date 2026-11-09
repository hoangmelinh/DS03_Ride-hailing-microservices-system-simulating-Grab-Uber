package com.goride.trip.application.dto.response;

import java.util.Collections;
import java.util.List;

public class TripDetailResponse {

    private TripResponse trip;
    private List<TripStatusHistoryResponse> history;

    public TripDetailResponse() {
        this.history = Collections.emptyList();
    }

    public TripDetailResponse(TripResponse trip, List<TripStatusHistoryResponse> history) {
        this.trip = trip;
        this.history = history != null ? history : Collections.emptyList();
    }

    public TripResponse getTrip() {
        return trip;
    }

    public void setTrip(TripResponse trip) {
        this.trip = trip;
    }

    public List<TripStatusHistoryResponse> getHistory() {
        return history;
    }

    public void setHistory(List<TripStatusHistoryResponse> history) {
        this.history = history;
    }
}

