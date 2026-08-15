package dev.rabauer.bahndemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient dbApiRestClient(BahnDemoProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.api().baseUrl())
                .build();
    }
}
