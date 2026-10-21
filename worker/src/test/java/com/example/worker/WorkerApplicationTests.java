package com.example.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.worker.enabled=false")
class WorkerApplicationTests {

    @Test
    void contextLoads() {
    }
}
