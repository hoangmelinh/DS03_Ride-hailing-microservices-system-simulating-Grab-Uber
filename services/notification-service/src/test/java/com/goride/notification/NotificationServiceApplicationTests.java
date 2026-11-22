package com.goride.notification;

import com.goride.notification.infrastructure.persistence.repository.NotificationDeadLetterRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
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
class NotificationServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private NotificationInboxEventRepository inboxEventRepository;

    @MockBean
    private NotificationDeadLetterRepository deadLetterRepository;

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
