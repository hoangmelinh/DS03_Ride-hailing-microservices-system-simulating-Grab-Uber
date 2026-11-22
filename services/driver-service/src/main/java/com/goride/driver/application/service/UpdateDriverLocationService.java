package com.goride.driver.application.service;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.driver.application.port.in.UpdateDriverLocationCommand;
import com.goride.driver.application.port.in.UpdateDriverLocationUseCase;
import com.goride.driver.domain.model.DriverLocation;
import com.goride.driver.domain.repository.DriverLocationRepository;
import com.goride.driver.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UpdateDriverLocationService implements UpdateDriverLocationUseCase {

    private final DriverRepository driverRepository;
    private final DriverLocationRepository driverLocationRepository;

    public UpdateDriverLocationService(DriverRepository driverRepository,
                                       DriverLocationRepository driverLocationRepository) {
        this.driverRepository = driverRepository;
        this.driverLocationRepository = driverLocationRepository;
    }

    @Override
    @Transactional
    public DriverLocation updateLocation(UpdateDriverLocationCommand command) {
        // Kiểm tra tài xế có tồn tại trong hệ thống hay không
        driverRepository.findById(command.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver", command.driverId()));

        // Cập nhật vị trí nếu đã có hoặc tạo mới nếu tài xế chưa từng cập nhật vị trí
        DriverLocation driverLocation = driverLocationRepository.findByDriverId(command.driverId())
                .map(loc -> {
                    loc.updateCoordinates(command.latitude(), command.longitude());
                    return loc;
                })
                .orElseGet(() -> new DriverLocation(
                        command.driverId(),
                        command.latitude(),
                        command.longitude(),
                        Instant.now()
                ));

        return driverLocationRepository.save(driverLocation);
    }
}
