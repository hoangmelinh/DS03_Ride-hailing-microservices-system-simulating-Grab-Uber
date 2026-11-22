package com.goride.matching.domain.repository;

import com.goride.matching.domain.model.DeadLetterMessage;

public interface DeadLetterMessageRepository {

    DeadLetterMessage save(DeadLetterMessage deadLetterMessage);
}
