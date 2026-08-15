package dev.rabauer.bahndemo;

import dev.rabauer.bahndemo.config.BahnDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BahnDemoProperties.class)
public class BahnDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BahnDemoApplication.class, args);
    }
}
