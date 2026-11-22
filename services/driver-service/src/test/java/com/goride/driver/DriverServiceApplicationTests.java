package com.goride.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(PostgresAvailableCondition.class)
class DriverServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
