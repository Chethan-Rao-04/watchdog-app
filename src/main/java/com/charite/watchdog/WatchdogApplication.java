package com.charite.watchdog;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableScheduling
@SpringBootApplication
public class WatchdogApplication {

	public static void main(String[] args) {
		SpringApplication.run(WatchdogApplication.class, args);
	}

}
