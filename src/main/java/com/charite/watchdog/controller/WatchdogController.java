package com.charite.watchdog.controller;

import com.charite.watchdog.model.MonitoredEntity;
import com.charite.watchdog.service.WatchdogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing monitored entities in the Watchdog system.
 * <p>
 * Provides endpoints for adding, retrieving, and removing entities monitored by the Watchdog service.
 * </p>
 *
 * @author Chethan Rao
 * @since 1.0
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
    public WatchdogController(WatchdogService watchdogService) {
        this.watchdogService = watchdogService;
    }

    /**
     * Adds a new entity to be monitored.
     * <p>
     * Validates input based on entity type (Docker or process).
     * </p>
     *
     * @param entity the MonitoredEntity to add for monitoring
     * @return a confirmation message indicating the entity was added
     * @throws IllegalArgumentException if required fields are missing
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
     * Retrieves all monitored entities.
     * <p>
     * Returns Docker containers and system processes with their current status.
     * </p>
     *
     * @return a List of all monitored entities
     */
    @GetMapping("/entities")
    public List<MonitoredEntity> getEntities() {
        return watchdogService.getEntities();
    }

    /**
     * Removes an entity from monitoring.
     * <p>
     * Supports container names and process PIDs as identifiers.
     * </p>
     *
     * @param id container name or process PID to remove
     * @return a confirmation message indicating the entity was removed
     * @throws IllegalArgumentException if the entity doesn't exist
     */
    @DeleteMapping("/entities/{id}")
    public String deleteEntity(@PathVariable String id) {
        if (id.matches("\\d+")) {
            // If ID is numeric, treat as PID
            Long pid = Long.parseLong(id);
            watchdogService.removeEntity(pid);
            return "Process with ID " + pid + " removed from monitoring";
        } else {
            // Otherwise treat as container name
            watchdogService.removeEntity(id);
            return "Docker Container " + id + " removed from monitoring";
        }
    }

    /**
     * Exception handler for validation errors.
     * <p>
     * Provides consistent error responses for client requests.
     * </p>
     *
     * @param ex the exception that was thrown
     * @return an error message describing the problem
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgumentException(IllegalArgumentException ex) {
        return "Error: " + ex.getMessage();
    }
}