package com.charite.watchdog.service;

import com.charite.watchdog.model.MonitoredEntity;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for monitoring Docker containers and system processes.
 * <p>
 * This service provides capabilities to add, remove, and monitor entities (Docker containers
 * and system processes). It periodically checks the status of monitored entities,
 * sends alerts when entities become inactive, and provides weekly summary reports.
 * </p>
 *
 * @author Chethan Rao
 * @since 1.0
 */
@Service
@Slf4j
public class WatchdogService {
    private final EmailService emailService;
    private final List<MonitoredEntity> entities = new ArrayList<>();
    private final DockerClient dockerClient;

    /** Thread-safe set to track entities that have already triggered alerts */
    private final Set<String> alertedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Date formatter for human-readable timestamps */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm:ss a");

    /**
     * Constructs a new WatchdogService with the specified email service.
     * Initializes the Docker client for container monitoring.
     *
     * @param emailService the service used to send email notifications
     */
    public WatchdogService(EmailService emailService) {
        this.emailService = emailService;
        this.dockerClient = DockerClientBuilder.getInstance().build();
        log.info("WatchdogService initialized with no static entities");
    }

    /**
     * Adds a new entity to be monitored.
     * <p>
     * For Docker containers, the name must be provided.
     * For system processes, the PID must be provided.
     * Prevents duplicate entities from being added.
     * </p>
     *
     * @param entity the entity to add for monitoring
     * @throws IllegalArgumentException if an entity with the same identifier already exists
     */
    public void addEntity(MonitoredEntity entity) {
        boolean exists = entities.stream().anyMatch(e ->
                (entity.isDocker() && e.isDocker() && entity.getName().equals(e.getName())) ||
                        (!entity.isDocker() && !e.isDocker() && entity.getPid().equals(e.getPid())));
        if (exists) throw new IllegalArgumentException("Entity already exists");
        entities.add(entity);
    }

    /**
     * Returns a copy of all monitored entities.
     * <p>
     * Creates a new list containing all currently monitored entities to prevent
     * external modification of the internal list.
     * </p>
     *
     * @return a list of all monitored entities
     */
    public List<MonitoredEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Periodically checks the status of all monitored entities.
     * <p>
     * This method is automatically scheduled to run at the interval specified
     * by the {@code watchdog.check-interval} property. It verifies the status
     * of each monitored entity and triggers alerts for inactive entities.
     * </p>
     */
    @Scheduled(fixedRateString = "${watchdog.check-interval}")
    public void checkStatus() {
        log.info("Starting status check at {}", LocalDateTime.now());
        for (MonitoredEntity entity : entities) {
            if (entity.isDocker()) {
                log.debug("Checking Docker entity: {}", entity.getName());
                checkDockerContainer(entity);
            } else {
                log.debug("Checking process entity: PID {}", entity.getPid());
                checkSystemProcess(entity);
            }
        }
        log.info("Status check completed at {}", LocalDateTime.now());
    }

    /**
     * Checks the status of a Docker container and sends alerts if it's down.
     * <p>
     * Verifies if the container is running using the Docker API.
     * If the container is not running and hasn't been alerted yet,
     * an alert email is sent and the container is marked as inactive.
     * </p>
     *
     * @param entity the Docker container entity to check
     */
    private void checkDockerContainer(MonitoredEntity entity) {
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            Optional<Container> containerOpt = containers.stream()
                    .filter(c -> c.getNames()[0].contains(entity.getName()))
                    .findFirst();

            if (containerOpt.isPresent()) {
                Container container = containerOpt.get();
                boolean isRunning = "running".equals(container.getState());

                if (isRunning) {
                    // Container is running - mark as active and remove from alerted set
                    entity.setActive(true);
                    alertedEntities.remove(entity.getName());
                } else if (!alertedEntities.contains(entity.getName())) {
                    // Container is not running and hasn't been alerted yet
                    entity.setActive(false);
                    String finishedAt = dockerClient.inspectContainerCmd(container.getId())
                            .exec()
                            .getState()
                            .getFinishedAt();

                    // Format the timestamp for better readability
                    String formattedTime;
                    if (finishedAt != null && !finishedAt.isEmpty()) {
                        LocalDateTime lastActiveTime = ZonedDateTime.parse(finishedAt)
                                .withZoneSameInstant(ZoneId.of("Europe/Berlin"))
                                .toLocalDateTime();
                        formattedTime = lastActiveTime.format(DATE_FORMATTER);
                    } else {
                        formattedTime = "unknown";
                    }

                    log.warn("Container {} stopped, last active at {}", entity.getName(), formattedTime);

                    // Send container down alert with improved formatting
                    sendContainerDownAlert(entity.getName(), formattedTime);
                    alertedEntities.add(entity.getName());
                }
            } else {
                if (!alertedEntities.contains(entity.getName())) {
                    // Container not found and hasn't been alerted yet
                    entity.setActive(false);
                    log.warn("Container {} not found", entity.getName());

                    // Send container missing alert with improved formatting
                    sendContainerMissingAlert(entity.getName());
                    alertedEntities.add(entity.getName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to check Docker container {}: {}", entity.getName(), e.getMessage(), e);
            emailService.sendEmail(
                    "Watchdog Error - Container " + entity.getName(),
                    "Dear Administrator,\n\n" +
                            "An error occurred while checking container " + entity.getName() + ":\n\n" +
                            e.getMessage() + "\n\n" +
                            "Please investigate this issue as soon as possible.\n\n" +
                            "Watchdog Monitoring Service"
            );
        }
    }

    /**
     * Sends a formatted alert email for a container that has stopped.
     * <p>
     * Formats an email with details about the stopped container
     * and sends it using the configured email service.
     * </p>
     *
     * @param containerName the name of the stopped container
     * @param lastActive formatted timestamp of when the container was last active
     */
    private void sendContainerDownAlert(String containerName, String lastActive) {
        emailService.sendEmail(
                "ALERT: Container '" + containerName + "' is DOWN",
                "Dear Administrator,\n\n" +
                        "This is an automated notification from Watchdog monitoring service.\n\n" +
                        "Container: " + containerName + "\n" +
                        "Status: DOWN\n" +
                        "Last active: " + lastActive + "\n\n" +
                        "Please investigate this issue as soon as possible.\n\n" +
                        "Watchdog Monitoring Service"
        );
    }

    /**
     * Sends a formatted alert email for a container that cannot be found.
     * <p>
     * Formats an email with details about the missing container
     * and sends it using the configured email service.
     * </p>
     *
     * @param containerName the name of the missing container
     */
    private void sendContainerMissingAlert(String containerName) {
        emailService.sendEmail(
                "ALERT: Container '" + containerName + "' is MISSING",
                "Dear Administrator,\n\n" +
                        "This is an automated notification from Watchdog monitoring service.\n\n" +
                        "The container '" + containerName + "' could not be found in the Docker environment.\n" +
                        "Status: MISSING\n\n" +
                        "This may indicate that the container has been removed or renamed.\n" +
                        "Please investigate this issue as soon as possible.\n\n" +
                        "Watchdog Monitoring Service"
        );
    }

    /**
     * Checks the status of a system process and sends alerts if it's down.
     * <p>
     * Verifies if the process with the given PID is alive using Java's ProcessHandle API.
     * If the process is not alive and hasn't been alerted yet, an alert email is sent
     * and the process is marked as inactive.
     * </p>
     *
     * @param entity the system process entity to check
     */
    private void checkSystemProcess(MonitoredEntity entity) {
        try {
            ProcessHandle.of(entity.getPid()).ifPresentOrElse(
                    handle -> entity.setActive(handle.isAlive()),
                    () -> {
                        entity.setActive(false);
                        if (!alertedEntities.contains(entity.getPid().toString())) {
                            // Process is not found and hasn't been alerted yet
                            entity.setLastActiveTimestamp(LocalDateTime.now().toString());

                            // Format the timestamp for better readability
                            String formattedTime = LocalDateTime.parse(entity.getLastActiveTimestamp())
                                    .format(DATE_FORMATTER);

                            log.warn("Process PID {} stopped at {}", entity.getPid(), formattedTime);

                            // Send process down alert with improved formatting
                            sendProcessDownAlert(entity.getPid(), formattedTime);
                            alertedEntities.add(entity.getPid().toString());
                        }
                    });
        } catch (Exception e) {
            log.error("Error checking process PID {}: {}", entity.getPid(), e.getMessage(), e);
            emailService.sendEmail(
                    "Watchdog Error - Process PID " + entity.getPid(),
                    "Dear Administrator,\n\n" +
                            "An error occurred while checking process with PID " + entity.getPid() + ":\n\n" +
                            e.getMessage() + "\n\n" +
                            "Please investigate this issue as soon as possible.\n\n" +
                            "Watchdog Monitoring Service"
            );
        }
    }

    /**
     * Sends a formatted alert email for a process that has stopped.
     * <p>
     * Formats an email with details about the stopped process
     * and sends it using the configured email service.
     * </p>
     *
     * @param pid the process ID of the stopped process
     * @param stoppedAt formatted timestamp of when the process was detected as stopped
     */
    private void sendProcessDownAlert(Long pid, String stoppedAt) {
        emailService.sendEmail(
                "ALERT: Process PID " + pid + " is DOWN",
                "Dear Administrator,\n\n" +
                        "This is an automated notification from Watchdog monitoring service.\n\n" +
                        "Process: PID " + pid + "\n" +
                        "Status: DOWN\n" +
                        "Detected at: " + stoppedAt + "\n\n" +
                        "Please investigate this issue as soon as possible.\n\n" +
                        "Watchdog Monitoring Service"
        );
    }

    /**
     * Sends a weekly summary report of all monitored entities.
     * <p>
     * This method is automatically scheduled to run at the time specified
     * by the {@code watchdog.summary-time} cron expression. It generates a
     * report containing the status of all monitored entities and sends it
     * via email.
     * </p>
     */
    @Scheduled(cron = "${watchdog.summary-time}")
    public void sendSummary() {
        StringBuilder summary = new StringBuilder("Weekly Monitoring Summary Report\n");
        summary.append("==================================\n\n");

        if (entities.isEmpty()) {
            summary.append("No monitored entities found.\n");
        } else {
            summary.append("Currently Monitored Entities:\n\n");

            // Count active and inactive entities
            int activeCount = 0;
            int inactiveCount = 0;

            for (MonitoredEntity entity : entities) {
                String status = entity.isActive() ? "ACTIVE" : "INACTIVE";
                if (entity.isActive()) activeCount++; else inactiveCount++;

                summary.append(String.format("• %s: %s\n",
                        entity.isDocker() ? "Docker Container '" + entity.getName() + "'" :
                                "System Process PID " + entity.getPid(),
                        status));
            }

            // Add summary statistics
            summary.append("\nSummary Statistics:\n");
            summary.append("- Total Monitored Entities: ").append(entities.size()).append("\n");
            summary.append("- Active: ").append(activeCount).append("\n");
            summary.append("- Inactive: ").append(inactiveCount).append("\n");

            summary.append("\n==================================\n");
            summary.append("Report generated on: ").append(LocalDateTime.now().format(DATE_FORMATTER));
            summary.append("\nWatchdog Monitoring Service");
        }

        emailService.sendEmail("Weekly Watchdog Monitoring Summary", summary.toString());
    }

    /**
     * Removes a Docker container from monitoring by its name.
     * <p>
     * Finds and removes the container with the given name from the monitored entities list.
     * If the container is not found, an exception is thrown.
     * </p>
     *
     * @param containerName the name of the Docker container to remove
     * @throws IllegalArgumentException if the container is not being monitored
     */
    public void removeEntity(String containerName) {
        boolean removed = entities.removeIf(e ->
                e.isDocker() && containerName.equals(e.getName()));

        if (!removed) {
            throw new IllegalArgumentException("Docker container '" + containerName + "' not found");
        }

        // Remove from alerted entities if present
        alertedEntities.remove(containerName);
        log.info("Docker container '{}' removed from monitoring", containerName);
    }

    /**
     * Removes a system process from monitoring by its PID.
     * <p>
     * Finds and removes the process with the given PID from the monitored entities list.
     * If the process is not found, an exception is thrown.
     * </p>
     *
     * @param pid the process ID of the system process to remove
     * @throws IllegalArgumentException if the process is not being monitored
     */
    public void removeEntity(Long pid) {
        boolean removed = entities.removeIf(e ->
                !e.isDocker() && pid.equals(e.getPid()));

        if (!removed) {
            throw new IllegalArgumentException("Process with PID " + pid + " not found");
        }

        // Remove from alerted entities if present
        alertedEntities.remove(pid.toString());
        log.info("Process with PID {} removed from monitoring", pid);
    }
}