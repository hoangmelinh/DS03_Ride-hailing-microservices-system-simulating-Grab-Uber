package com.goride.matching.application.service;

import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort.RankedDriverCandidate;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import com.goride.matching.application.port.out.DriverServiceClientPort.AvailableDriverCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateDriverDiscoveryServiceTest {

    @Mock
    private DriverServiceClientPort driverServiceClientPort;

    @Test
    @DisplayName("Should rank available drivers by Haversine distance and place drivers without coordinates last")
    void findCandidates_ShouldSortByDistanceAndPlaceNullLocationLast() {
        CandidateDriverDiscoveryService discoveryService = new CandidateDriverDiscoveryService(driverServiceClientPort, 10);

        UUID noLocDriver = UUID.randomUUID();
        UUID farDriver = UUID.randomUUID();
        UUID nearDriver = UUID.randomUUID();

        when(driverServiceClientPort.getAvailableDrivers(20)).thenReturn(List.of(
                new AvailableDriverCandidate(noLocDriver, "AVAILABLE", null, null, null),
                new AvailableDriverCandidate(farDriver, "AVAILABLE", new BigDecimal("10.800000"), new BigDecimal("106.700000"), Instant.now()),
                new AvailableDriverCandidate(nearDriver, "AVAILABLE", new BigDecimal("10.763000"), new BigDecimal("106.661000"), Instant.now())
        ));

        List<RankedDriverCandidate> ranked = discoveryService.findCandidates(
                new BigDecimal("10.762622"), new BigDecimal("106.660172"));

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).driverId()).isEqualTo(nearDriver);
        assertThat(ranked.get(0).rank()).isEqualTo(1);
        assertThat(ranked.get(1).driverId()).isEqualTo(farDriver);
        assertThat(ranked.get(1).rank()).isEqualTo(2);
        assertThat(ranked.get(2).driverId()).isEqualTo(noLocDriver);
        assertThat(ranked.get(2).distanceMeters()).isNull();
    }
}
