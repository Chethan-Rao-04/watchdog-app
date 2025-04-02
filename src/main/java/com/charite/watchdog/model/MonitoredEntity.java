package com.charite.watchdog.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an entity monitored by the Watchdog system.
 * Can represent either a Docker container or a system process.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitoredEntity {
    /** The name of the Docker container, null for system processes */
    private String name;

    /** The process ID of the system process, null for Docker containers */
    private Long pid;

    /** Indicates whether this entity is a Docker container (true) or system process (false) */
    private boolean isDocker;

    /** Current status of the entity (active/inactive) */
    private boolean isActive;

    /** Timestamp of when the entity was last active, null if never inactive */
    private String lastActiveTimestamp;
}