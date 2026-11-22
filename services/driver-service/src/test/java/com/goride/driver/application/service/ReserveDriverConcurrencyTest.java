package com.goride.driver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.driver.application.exception.DriverNotAvailableException;
import com.goride.driver.application.port.in.ReserveDriverCommand;
import com.goride.driver.application.port.in.ReserveDriverResult;
import com.goride.driver.domain.model.Driver;
import com.goride.driver.domain.model.DriverReservation;
import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.OutboxEvent;
import com.goride.driver.domain.model.ReservationStatus;
import com.goride.driver.domain.repository.DriverRepository;
import com.goride.driver.domain.repository.DriverReservationRepository;
import com.goride.driver.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReserveDriverConcurrencyTest {

    private DriverRepository driverRepository;
    private DriverReservationRepository driverReservationRepository;
    private OutboxEventRepository outboxEventRepository;
    private ObjectMapper objectMapper;
    private ReserveDriverService reserveDriverService;

    private UUID driverId;
    private Driver driver;

    // Concurrent in-memory storage simulating DB atomic behavior
    private final ConcurrentHashMap<String, DriverReservation> reservationsByIdempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DriverReservation> activeReservationsByTrip = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<OutboxEvent> savedOutboxEvents = new CopyOnWriteArrayList<>();
    private final AtomicReference<UUID> reservedTripWinner = new AtomicReference<>(null);

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        driver = new Driver(
                driverId,
                UUID.randomUUID(),
                "GPLX-99887766",
                DriverStatus.AVAILABLE,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );

        reservationsByIdempotency.clear();
        activeReservationsByTrip.clear();
        savedOutboxEvents.clear();
        reservedTripWinner.set(null);

        driverRepository = mock(DriverRepository.class);
        driverReservationRepository = mock(DriverReservationRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        objectMapper = new ObjectMapper();

        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        // Atomic reservation mock: atomic compare-and-set at DB level
        when(driverRepository.atomicReserve(eq(driverId), any(UUID.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    UUID tripId = invocation.getArgument(1);
                    return reservedTripWinner.compareAndSet(null, tripId);
                });

        // Driver reservation repository mock
        when(driverReservationRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return Optional.ofNullable(reservationsByIdempotency.get(key));
                });

        when(driverReservationRepository.findActiveByTripId(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID tripId = invocation.getArgument(0);
                    return Optional.ofNullable(activeReservationsByTrip.get(tripId));
                });

        when(driverReservationRepository.save(any(DriverReservation.class)))
                .thenAnswer(invocation -> {
                    DriverReservation res = invocation.getArgument(0);
                    reservationsByIdempotency.put(res.getIdempotencyKey(), res);
                    activeReservationsByTrip.put(res.getTripId(), res);
                    return res;
                });

        // Outbox repository mock
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> {
                    OutboxEvent event = invocation.getArgument(0);
                    savedOutboxEvents.add(event);
                    return event;
                });

        reserveDriverService = new ReserveDriverService(
                driverRepository,
                driverReservationRepository,
                outboxEventRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("CONCURRENCY: When two simultaneous threads attempt to reserve the same driver, exactly 1 succeeds and 1 gets 409")
    void testConcurrentReservationContention_SingleWinner() throws Exception {
        UUID trip1 = UUID.randomUUID();
        UUID trip2 = UUID.randomUUID();
        String idemp1 = "idemp-thread-1";
        String idemp2 = "idemp-thread-2";
        UUID correlation1 = UUID.randomUUID();
        UUID correlation2 = UUID.randomUUID();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicReference<ReserveDriverResult> winningResult = new AtomicReference<>(null);

        List<Future<?>> futures = new ArrayList<>();

        // Thread 1
        futures.add(executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                ReserveDriverResult res = reserveDriverService.reserveDriver(
                        new ReserveDriverCommand(driverId, trip1, idemp1, correlation1, 30));
                successCount.incrementAndGet();
                winningResult.set(res);
            } catch (DriverNotAvailableException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // unexpected
            }
        }));

        // Thread 2
        futures.add(executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                ReserveDriverResult res = reserveDriverService.reserveDriver(
                        new ReserveDriverCommand(driverId, trip2, idemp2, correlation2, 30));
                successCount.incrementAndGet();
                winningResult.set(res);
            } catch (DriverNotAvailableException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // unexpected
            }
        }));

        // Start both threads simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // INVARIANT CHECKS
        // 1. Exactly 1 winner, 1 conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // 2. Winning result matches the DB winner trip
        UUID winnerTrip = reservedTripWinner.get();
        assertThat(winnerTrip).isNotNull();
        assertThat(winningResult.get()).isNotNull();
        assertThat(winningResult.get().tripId()).isEqualTo(winnerTrip);
        assertThat(winningResult.get().status()).isEqualTo(ReservationStatus.RESERVED);

        // 3. Exactly 1 outbox event created
        assertThat(savedOutboxEvents).hasSize(1);
        assertThat(savedOutboxEvents.get(0).getEventType()).isEqualTo("driver.reserved");
        assertThat(savedOutboxEvents.get(0).getAggregateId()).isEqualTo(driverId);
        assertThat(savedOutboxEvents.get(0).getPayload()).contains(winnerTrip.toString());

        // 4. Exactly 1 active reservation in storage
        assertThat(reservationsByIdempotency).hasSize(1);
    }
}

