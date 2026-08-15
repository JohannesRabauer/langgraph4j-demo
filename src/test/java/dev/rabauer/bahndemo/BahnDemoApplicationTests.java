package dev.rabauer.bahndemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Plain context-load smoke test: catches wiring mistakes across the whole app (Vaadin, langgraph4j graph, Spring AI autoconfiguration). */
@SpringBootTest
class BahnDemoApplicationTests {

    @Test
    void contextLoads() {
    }
}
