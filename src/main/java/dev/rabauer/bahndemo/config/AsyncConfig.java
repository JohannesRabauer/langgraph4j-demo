package dev.rabauer.bahndemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class AsyncConfig {

    /**
     * Dedicated pool for delay-workflow runs, kept off both the Vaadin UI thread and the scheduler
     * thread. Used directly by WorkflowOrchestrationService's hand-rolled orchestration today; a
     * langgraph4j CompiledGraph can reuse it as its own executor once the graph is wired live.
     */
    @Bean
    public ExecutorService workflowExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
