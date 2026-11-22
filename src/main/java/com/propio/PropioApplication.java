package com.propio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Propio Property Management SaaS Application Entry Point.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PropioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropioApplication.class, args);
    }
}
