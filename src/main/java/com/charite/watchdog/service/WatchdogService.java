package com.charite.watchdog.service;

import com.charite.watchdog.model.MonitoredEntity;
import com.charite.watchdog.service.EmailService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class WatchdogService {
    private final EmailService emailService;
    private final List<MonitoredEntity> entities = new ArrayList<>();
    private final DockerClient dockerClient;
    // Thread-safe set to track entities that have already triggered alerts
    private final Set<String> alertedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Logger logger = LoggerFactory.getLogger(WatchdogService.class);
    // Date formatter for human-readable timestamps
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm:ss a");

    /**
     * Constructs a new WatchdogService with the specified email service.
     * Initializes the Docker client for container monitoring.
     */
    public WatchdogService(EmailService emailService) {
        this.emailService = emailService;
        this.dockerClient = DockerClientBuilder.getInstance().build();
        logger.info("WatchdogService initialized with no static entities");
    }
    /**
     * Adds a new entity to be monitored.
     * Prevents duplicate entities from being added.
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

     */
    public List<MonitoredEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Periodically checks the status of all monitored entities.
     * The check interval is configured in application properties.
     */
    @Scheduled(fixedRateString = "${watchdog.check-interval}")
    public void checkStatus() {
        logger.info("Starting status check at {}", LocalDateTime.now());
        for (MonitoredEntity entity : entities) {
            if (entity.isDocker()) {
                logger.debug("Checking Docker entity: {}", entity.getName());
                checkDockerContainer(entity);
            } else {
                logger.debug("Checking process entity: PID {}", entity.getPid());
                checkSystemProcess(entity);
            }
        }
        logger.info("Status check completed at {}", LocalDateTime.now());
    }


    /**
     * Checks the status of a Docker container and sends alerts if it's down.
     *
     * @param entity the Docker container entity to check
     */
    /**
     * Checks the status of a Docker container and sends alerts if it's down.
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

                    logger.warn("Container {} stopped, last active at {}", entity.getName(), formattedTime);

                    // Send container down alert with improved formatting
                    sendContainerDownAlert(entity.getName(), formattedTime);
                    alertedEntities.add(entity.getName());
                }
            } else {
                if (!alertedEntities.contains(entity.getName())) {
                    // Container not found and hasn't been alerted yet
                    entity.setActive(false);
                    logger.warn("Container {} not found", entity.getName());

                    // Send container missing alert with improved formatting
                    sendContainerMissingAlert(entity.getName());
                    alertedEntities.add(entity.getName());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to check Docker container {}: {}", entity.getName(), e.getMessage(), e);
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

                            logger.warn("Process PID {} stopped at {}", entity.getPid(), formattedTime);

                            // Send process down alert with improved formatting
                            sendProcessDownAlert(entity.getPid(), formattedTime);
                            alertedEntities.add(entity.getPid().toString());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error checking process PID {}: {}", entity.getPid(), e.getMessage(), e);
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
     * The schedule is configured in application properties.
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
}