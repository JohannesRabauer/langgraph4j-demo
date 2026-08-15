package dev.rabauer.bahndemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bahn")
public record BahnDemoProperties(Api api, Delay delay, Advisor advisor) {

    public record Api(String baseUrl, int defaultResults) {
    }

    public record Delay(int thresholdSeconds, long pollIntervalMs) {
    }

    public record Advisor(boolean enabled) {
    }
}
