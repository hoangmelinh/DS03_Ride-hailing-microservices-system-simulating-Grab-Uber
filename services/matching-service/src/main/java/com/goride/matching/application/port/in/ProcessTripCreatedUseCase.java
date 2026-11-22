package com.goride.matching.application.port.in;

import com.goride.matching.domain.model.MatchingSaga;

public interface ProcessTripCreatedUseCase {

    MatchingSaga processTripCreated(ProcessTripCreatedCommand command);
}
