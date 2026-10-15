package com.goride.driver.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Vehicle {

    private final UUID id;
    private final UUID driverId;
    private String plateNumber;
    private String brand;
    private String model;
    private String color;
    private int capacity;
    private VehicleStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Vehicle(UUID id, UUID driverId, String plateNumber, String brand,
            String model, String color, int capacity, VehicleStatus status,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.driverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.plateNumber = Objects.requireNonNull(plateNumber, "plateNumber must not be null");
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.capacity = capacity > 0 ? capacity : 4;
        this.status = status != null ? status : VehicleStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = Objects.requireNonNull(plateNumber, "plateNumber must not be null");
        this.updatedAt = Instant.now();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
        this.updatedAt = Instant.now();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
        this.updatedAt = Instant.now();
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        this.updatedAt = Instant.now();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
