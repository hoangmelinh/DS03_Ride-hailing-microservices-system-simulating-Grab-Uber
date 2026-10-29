package com.goride.notification;

import com.goride.notification.infrastructure.persistence.repository.NotificationDeadLetterRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationInboxEventRepository;
import com.goride.notification.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class NotificationServiceApplicationTests {

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private NotificationInboxEventRepository inboxEventRepository;

    @MockBean
    private NotificationDeadLetterRepository deadLetterRepository;

    @Test
    void contextLoads() {
    }
}
