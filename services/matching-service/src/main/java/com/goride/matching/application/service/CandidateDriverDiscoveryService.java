package com.goride.matching.application.service;

import com.goride.matching.application.port.out.CandidateDriverDiscoveryPort;
import com.goride.matching.application.port.out.DriverServiceClientPort;
import com.goride.matching.application.port.out.DriverServiceClientPort.AvailableDriverCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CandidateDriverDiscoveryService implements CandidateDriverDiscoveryPort {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final DriverServiceClientPort driverServiceClientPort;
    private final int maxCandidates;

    public CandidateDriverDiscoveryService(
            DriverServiceClientPort driverServiceClientPort,
            @Value("${goride.matching.max-candidates:10}") int maxCandidates) {
        this.driverServiceClientPort = driverServiceClientPort;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public List<RankedDriverCandidate> findCandidates(BigDecimal pickupLatitude, BigDecimal pickupLongitude) {
        List<AvailableDriverCandidate> available = driverServiceClientPort.getAvailableDrivers(maxCandidates * 2);
        if (available == null || available.isEmpty()) {
            return List.of();
        }

        record ScoredDriver(AvailableDriverCandidate driver, Integer distanceMeters) {}

        List<ScoredDriver> sorted = available.stream()
                .map(d -> new ScoredDriver(d, computeDistanceOrNull(pickupLatitude, pickupLongitude, d.latitude(), d.longitude())))
                .sorted(Comparator.comparing(ScoredDriver::distanceMeters, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(maxCandidates)
                .toList();

        List<RankedDriverCandidate> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ScoredDriver sd = sorted.get(i);
            ranked.add(new RankedDriverCandidate(sd.driver().driverId(), i + 1, sd.distanceMeters()));
        }
        return ranked;
    }

    private Integer computeDistanceOrNull(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double rLat1 = Math.toRadians(lat1.doubleValue());
        double rLat2 = Math.toRadians(lat2.doubleValue());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
