package com.charite.watchdog.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an entity monitored by the Watchdog system.
 * <p>
 * This class serves as a data model for entities monitored by the Watchdog service.
 * It supports Docker containers (identified by name) and system processes (identified by PID).
 * </p>
 *
 * @author Chethan Rao
 * @since 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitoredEntity {

    /**
     * The name of the Docker container.
     * <p>
     * Used as identifier for Docker containers.
     * </p>
     */
    private String name;

    /**
     * The process ID of the system process.
     * <p>
     * Used as identifier for system processes.
     * </p>
     */
    private Long pid;

    /**
     * The timestamp of when the entity was last active.
     * <p>
     * Records when a previously active entity became inactive.
     * </p>
     */
    private String lastActiveTimestamp;

    /**
     * Indicates whether the entity is currently active.
     * <p>
     * True if container is running or process is alive.
     * </p>
     */
    private boolean isActive;

    /**
     * Indicates whether this entity is a Docker container.
     * <p>
     * Determines which identifier (name or PID) is used.
     * </p>
     */
    private boolean isDocker;
}