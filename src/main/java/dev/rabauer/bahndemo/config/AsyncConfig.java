package dev.rabauer.bahndemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class AsyncConfig {

    /** Dedicated pool for langgraph4j graph runs, kept off both the Vaadin UI thread and the scheduler thread. */
    @Bean
    public ExecutorService workflowExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
