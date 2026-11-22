package com.goride.driver.application.service;

import com.goride.driver.application.port.in.AvailableDriverResult;
import com.goride.driver.application.port.in.GetAvailableDriversUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.repository.DriverLocationRepository;
import com.goride.driver.domain.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GetAvailableDriversService implements GetAvailableDriversUseCase {

    private final DriverRepository driverRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final int defaultLimit;
    private final int maxLimit;

    public GetAvailableDriversService(
            DriverRepository driverRepository,
            DriverLocationRepository driverLocationRepository,
            @Value("${goride.driver.available-default-limit:20}") int defaultLimit,
            @Value("${goride.driver.available-max-limit:100}") int maxLimit) {
        this.driverRepository = driverRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableDriverResult> getAvailableDrivers(Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, maxLimit) : defaultLimit;

        List<Driver> availableDrivers = driverRepository.findByStatus(DriverStatus.AVAILABLE)
                .stream()
                .limit(effectiveLimit)
                .toList();

        if (availableDrivers.isEmpty()) {
            return List.of();
        }

        List<UUID> driverIds = availableDrivers.stream().map(Driver::getId).toList();
        Map<UUID, DriverLocation> locationsByDriverId = driverLocationRepository.findByDriverIds(driverIds)
                .stream()
                .collect(Collectors.toMap(DriverLocation::getDriverId, Function.identity(), (a, b) -> a));

        return availableDrivers.stream()
                .map(driver -> {
                    DriverLocation loc = locationsByDriverId.get(driver.getId());
                    return new AvailableDriverResult(
                            driver.getId(),
                            driver.getStatus(),
                            loc != null ? loc.getLatitude() : null,
                            loc != null ? loc.getLongitude() : null,
                            loc != null ? loc.getUpdatedAt() : null
                    );
                })
                .toList();
    }
}
