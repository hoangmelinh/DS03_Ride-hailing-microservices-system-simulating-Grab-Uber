package com.goride.matching.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CandidateDriverDiscoveryPort {

    List<RankedDriverCandidate> findCandidates(BigDecimal pickupLatitude, BigDecimal pickupLongitude);

    record RankedDriverCandidate(
            UUID driverId,
            int rank,
            Integer distanceMeters
    ) {
    }
}
