package com.goride.payment;

import com.goride.payment.infrastructure.persistence.repository.InboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentAttemptRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentDeadLetterRepository;
import com.goride.payment.infrastructure.persistence.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentAttemptRepository paymentAttemptRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private InboxEventRepository inboxEventRepository;

    @MockBean
    private PaymentDeadLetterRepository deadLetterRepository;

    @MockBean
    private ConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("Actuator health endpoint returns status UP without exposing details (AUD-P1-009)")
    void healthEndpoint_ReturnsStatusWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
