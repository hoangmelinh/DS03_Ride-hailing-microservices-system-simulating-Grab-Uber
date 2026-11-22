package com.goride.trip.application.dto.response;

import java.math.BigDecimal;

public class FareEstimateResponse {

    private BigDecimal distanceKm;
    private BigDecimal estimatedFare;
    private String currency = "VND";
    private BigDecimal baseFare;
    private BigDecimal ratePerKm;

    public FareEstimateResponse() {
    }

    public FareEstimateResponse(BigDecimal distanceKm, BigDecimal estimatedFare, String currency,
                                BigDecimal baseFare, BigDecimal ratePerKm) {
        this.distanceKm = distanceKm;
        this.estimatedFare = estimatedFare;
        this.currency = currency != null ? currency : "VND";
        this.baseFare = baseFare;
        this.ratePerKm = ratePerKm;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(BigDecimal estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public BigDecimal getRatePerKm() {
        return ratePerKm;
    }

    public void setRatePerKm(BigDecimal ratePerKm) {
        this.ratePerKm = ratePerKm;
    }
}

