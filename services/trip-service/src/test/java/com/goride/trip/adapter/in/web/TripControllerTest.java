package com.goride.trip.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.trip.application.dto.request.CreateTripRequest;
import com.goride.trip.application.dto.request.EstimateFareRequest;
import com.goride.trip.application.dto.response.FareEstimateResponse;
import com.goride.trip.application.dto.response.TripDetailResponse;
import com.goride.trip.application.dto.response.TripResponse;
import com.goride.trip.application.dto.response.TripStatusHistoryResponse;
import com.goride.trip.application.service.TripService;
import com.goride.trip.domain.model.ActorType;
import com.goride.trip.domain.model.TripStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TripController.class, TripGlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TripService tripService;

    @Test
    @DisplayName("POST /api/v1/trips: Successfully creates trip and returns 201 Created")
    void createTrip_Success_Returns201() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CreateTripRequest request = new CreateTripRequest(
                passengerId,
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                "Bitexco Financial Tower",
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664"),
                "Tan Son Nhat Airport"
        );
        request.setEstimatedFare(new BigDecimal("95000.00"));

        TripResponse mockResponse = new TripResponse();
        mockResponse.setId(tripId);
        mockResponse.setPassengerId(passengerId);
        mockResponse.setStatus(TripStatus.REQUESTED);
        mockResponse.setEstimatedFare(new BigDecimal("95000.00"));
        mockResponse.setCurrency("VND");

        when(tripService.createTrip(any(CreateTripRequest.class), eq(correlationId))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/trips")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-User-Id", passengerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tripId.toString()))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.estimatedFare").value(95000.00))
                .andExpect(jsonPath("$.data.currency").value("VND"));
    }

    @Test
    @DisplayName("POST /api/v1/trips: Fails validation on missing pickup latitude and returns 400 Bad Request")
    void createTrip_ValidationFailure_Returns400() throws Exception {
        CreateTripRequest invalidRequest = new CreateTripRequest();
        invalidRequest.setPassengerId(UUID.randomUUID());
        // Coordinates left null

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/trips/{id}: Returns 200 OK when trip exists")
    void getTripById_Success_Returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        TripResponse mockResponse = new TripResponse();
        mockResponse.setId(tripId);
        mockResponse.setStatus(TripStatus.REQUESTED);

        when(tripService.getTripById(tripId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/trips/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tripId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/trips/{id}: Returns 404 Not Found when trip does not exist")
    void getTripById_NotFound_Returns404() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripService.getTripById(tripId)).thenThrow(new EntityNotFoundException("Trip", tripId));

        mockMvc.perform(get("/api/v1/trips/{id}", tripId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/trips/{id}/detail: Returns 200 OK with status history list")
    void getTripDetail_Success_Returns200() throws Exception {
        UUID tripId = UUID.randomUUID();
        TripResponse trip = new TripResponse();
        trip.setId(tripId);
        trip.setStatus(TripStatus.REQUESTED);

        TripStatusHistoryResponse history = new TripStatusHistoryResponse();
        history.setId(UUID.randomUUID());
        history.setTripId(tripId);
        history.setToStatus("REQUESTED");
        history.setActorType(ActorType.USER);
        history.setChangedAt(Instant.now());

        TripDetailResponse detail = new TripDetailResponse(trip, List.of(history));

        when(tripService.getTripDetail(tripId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/trips/{id}/detail", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip.id").value(tripId.toString()))
                .andExpect(jsonPath("$.data.history[0].toStatus").value("REQUESTED"));
    }

    @Test
    @DisplayName("GET /api/v1/trips/passenger/{passengerId}: Returns 200 OK with paginated list")
    void getTripsByPassenger_Success_Returns200() throws Exception {
        UUID passengerId = UUID.randomUUID();
        TripResponse trip = new TripResponse();
        trip.setId(UUID.randomUUID());
        trip.setPassengerId(passengerId);

        when(tripService.getTripsByPassenger(eq(passengerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip)));

        mockMvc.perform(get("/api/v1/trips/passenger/{passengerId}", passengerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].passengerId").value(passengerId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/trips/estimate-fare: Returns 200 OK with distance and fare estimate")
    void estimateFare_Success_Returns200() throws Exception {
        EstimateFareRequest request = new EstimateFareRequest(
                new BigDecimal("10.776889"),
                new BigDecimal("106.700806"),
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664")
        );

        FareEstimateResponse response = new FareEstimateResponse(
                new BigDecimal("9.45"),
                new BigDecimal("89500.00"),
                "VND",
                new BigDecimal("15000.00"),
                new BigDecimal("10000.00")
        );

        when(tripService.estimateFare(any(EstimateFareRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/trips/estimate-fare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distanceKm").value(9.45))
                .andExpect(jsonPath("$.data.estimatedFare").value(89500.00))
                .andExpect(jsonPath("$.data.currency").value("VND"));
    }

    @Test
    @DisplayName("POST /api/v1/trips/estimate-fare: Fails validation on invalid latitude (> 90.0) with 400 Bad Request")
    void estimateFare_InvalidCoordinate_Returns400() throws Exception {
        EstimateFareRequest invalidRequest = new EstimateFareRequest(
                new BigDecimal("95.0"), // invalid latitude
                new BigDecimal("106.700806"),
                new BigDecimal("10.823099"),
                new BigDecimal("106.629664")
        );

        mockMvc.perform(post("/api/v1/trips/estimate-fare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}

