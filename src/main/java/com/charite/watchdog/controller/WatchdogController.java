package com.charite.watchdog.controller;

import com.charite.watchdog.config.WatchdogProperties;
import com.charite.watchdog.model.MonitoredEntity;
import com.charite.watchdog.service.WatchdogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing monitored entities in the Watchdog system.
 * Provides endpoints for adding and retrieving monitored entities.
 */
@Slf4j
@RestController
@RequestMapping("watchdog")
public class WatchdogController {
    private final WatchdogService watchdogService;
    private final WatchdogProperties properties;
    private static final String CONFIG_FILE = "watchdog-config.yml";

    /**
     * Constructs a new WatchdogController with the specified WatchdogService.
     */
    public WatchdogController(WatchdogService watchdogService, WatchdogProperties properties) {
        this.watchdogService = watchdogService;
        this.properties = properties;
        loadInitialConfig();
    }

    /**
     * Loads initial configuration from watchdog-config.yml if it exists.
     * Falls back to defaults in WatchdogProperties if file is absent.
     */
    public void loadInitialConfig() {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            Map<String, String> config = yaml.load(fis);
            if (config != null) {
                properties.setCheckInterval(config.getOrDefault("check-interval", properties.getCheckInterval()));
                properties.setSummaryInterval(config.getOrDefault("summary-interval", properties.getSummaryInterval()));
                properties.setEmailRecipients(config.getOrDefault("email-recipients", properties.getEmailRecipients()));
                properties.setMailUsername(config.getOrDefault("mail-username", properties.getMailUsername()));
                properties.setMailPassword(config.getOrDefault("mail-password", properties.getMailPassword()));
            }
            watchdogService.rescheduleTasks(); // Apply loaded intervals
        } catch (IOException e) {
            log.error("Failed to load initial config from YAML: {}", e.getMessage(), e);
        }
    }


    /**
     * Adds a new entity to be monitored by the Watchdog service.
     * Supports both Docker containers (requiring a name) and system processes (requiring a PID).
     */

    @PostMapping("/entities")
    public String addEntity(@RequestBody MonitoredEntity entity) {
        if (entity.isDocker()) {
            if (entity.getName() == null) throw new IllegalArgumentException("Name is required for Docker entities");
            entity.setPid(null); // Ensure pid is null for Docker
            watchdogService.addEntity(entity);
            return "Docker Container " + entity.getName() + " added";
        }
        if (entity.getPid() == null) throw new IllegalArgumentException("PID is required for process entities");
        entity.setName(null); // Ensure name is null for processes
        watchdogService.addEntity(entity);
        return "Process with ID " + entity.getPid() + " added.";
    }

    /**
     * Fetches the new config from the request
     * updates the in-memory watchdog properties
     * and also persists it to a custom YAML file
     */
    @PatchMapping("/config")
    public String updateConfig(@RequestBody WatchdogProperties newConfig) throws IOException {
        // Update in-memory properties.
        if (newConfig.getCheckInterval() != null) properties.setCheckInterval(newConfig.getCheckInterval());
        if (newConfig.getSummaryInterval() != null) properties.setSummaryInterval(newConfig.getSummaryInterval());
        if (newConfig.getEmailRecipients() != null) properties.setEmailRecipients(newConfig.getEmailRecipients());
        if (newConfig.getMailUsername() != null) properties.setMailUsername(newConfig.getMailUsername());
        if (newConfig.getMailPassword() != null) properties.setMailPassword(newConfig.getMailPassword());


        // Persist to custom YAML file
        Map<String, String> configMap = new LinkedHashMap<>();
        configMap.put("check-interval", properties.getCheckInterval());
        configMap.put("summary-interval", properties.getSummaryInterval());
        configMap.put("email-recipients", properties.getEmailRecipients());
        configMap.put("mail-username", properties.getMailUsername());
        configMap.put("mail-password", properties.getMailPassword());

        // Customise the YAML output using Dumper options
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

       /* Initializes a SnakeYAML Yaml object using the configured options.
        Opens a FileWriter to write to CONFIG_FILE (some predefined path).
        Dumps the configMap into that file as YAML.*/
        Yaml yaml = new Yaml(options);
        try (FileWriter fw = new FileWriter(CONFIG_FILE)) {
            yaml.dump(configMap, fw);
        }
        return "Configuration updated successfully";
    }

    // Retrieves the list of all monitored entities.
    @GetMapping("/entities")
    public List<MonitoredEntity> getEntities() {
        return watchdogService.getEntities();
    }

    @GetMapping("/config")
    public WatchdogProperties getConfig() {
        return properties;
    }
}