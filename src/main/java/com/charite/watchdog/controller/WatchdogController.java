package com.charite.watchdog.controller;

import com.charite.watchdog.model.MonitoredEntity;
import com.charite.watchdog.service.WatchdogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing monitored entities in the Watchdog system.
 * Provides endpoints for adding and retrieving monitored entities.
 */
@RestController
@RequestMapping("watchdog")
public class WatchdogController {

    private final WatchdogService watchdogService;
    /**
     * Constructs a new WatchdogController with the specified WatchdogService.
     *
     * @param watchdogService the service handling entity monitoring operations
     */
    public WatchdogController(WatchdogService watchdogService){
        this.watchdogService = watchdogService;
    }

    /**
     * Adds a new entity to be monitored by the Watchdog service.
     * Supports both Docker containers (requiring a name) and system processes (requiring a PID).
     *
     * @param entity the MonitoredEntity to add
     * @return a confirmation message indicating the entity was added
     * @throws IllegalArgumentException if required fields (name for Docker, PID for process) are missing
     */

    @PostMapping("/entities")
    public String addEntity(@RequestBody MonitoredEntity entity) {
        if (entity.isDocker()) {
            if (entity.getName() == null) {
                throw new IllegalArgumentException("Name is required for Docker entities");
            }
            entity.setPid(null); // Ensure pid is null for Docker
            watchdogService.addEntity(entity);
            return "Docker Container " + entity.getName() + " added";
        }
        if (entity.getPid() == null) {
            throw new IllegalArgumentException("PID is required for process entities");
        }
        entity.setName(null); // Ensure name is null for processes
        watchdogService.addEntity(entity);
        return "Process with ID " + entity.getPid() + " added.";
    }

    /**
     * Retrieves the list of all monitored entities.
     *
     * @return a List of all MonitoredEntity objects currently being monitored
     */
    @GetMapping("/entities")
    public List<MonitoredEntity> getEntities(){
        return watchdogService.getEntities();
    }
}