package com.charite.watchdog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "watchdog")
public class WatchdogProperties {

    private String checkInterval = "600000"; // Default 10 minutes
    private String summaryInterval = "0 0 9 * * SUN"; // Default Sunday 7:14 PM
    private String emailRecipients;
    private String mailUsername;
    private String mailPassword;

}
