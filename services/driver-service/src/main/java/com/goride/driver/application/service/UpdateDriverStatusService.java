package com.goride.driver.application.service;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.InvalidRequestException;
import com.goride.driver.application.port.in.UpdateDriverStatusCommand;
import com.goride.driver.application.port.in.UpdateDriverStatusUseCase;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateDriverStatusService implements UpdateDriverStatusUseCase {

    private final DriverRepository driverRepository;

    public UpdateDriverStatusService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    @Transactional
    public Driver updateDriverStatus(UpdateDriverStatusCommand command) {
        Driver driver = driverRepository.findById(command.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver", command.driverId()));

        String requestedStatus = command.status() != null ? command.status().trim().toUpperCase() : "";

        switch (requestedStatus) {
            case "ONLINE", "AVAILABLE" -> driver.goOnline();
            case "OFFLINE" -> driver.goOffline();
            default -> throw new InvalidRequestException(
                    "Invalid status: '" + command.status() + "'. Allowed values: ONLINE, OFFLINE");
        }

        return driverRepository.save(driver);
    }
}
