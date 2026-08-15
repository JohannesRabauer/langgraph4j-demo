package dev.rabauer.bahndemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Context-load check with bahn.advisor.enabled=true - the configuration docker-compose.yml uses.
 * Regression guard for a real startup bug found while testing the Docker stack: both
 * RuleBasedAdvisorService and LlmAdvisorService become active AdvisorService beans in this mode,
 * and AdvisorNode's single-bean injection point failed until LlmAdvisorService was marked @Primary.
 * Building the ChatClient doesn't require a reachable Ollama server, so this needs no live LLM.
 */
@SpringBootTest
@TestPropertySource(properties = "bahn.advisor.enabled=true")
class AdvisorEnabledContextLoadsTest {

    @Test
    void contextLoadsWithLlmAdvisorActive() {
    }
}
