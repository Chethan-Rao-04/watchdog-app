package com.charite.watchdog;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Watchdog monitoring application.
 * <p>
 * Configures and launches the Spring Boot application with scheduling enabled.
 * </p>
 *
 * @author Chethan Rao
 * @since 1.0
 */
@EnableScheduling
@SpringBootApplication
public class WatchdogApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchdogApplication.class, args);
    }
}