package com.charite.watchdog.service;

import com.charite.watchdog.config.WatchdogProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.charite.watchdog.model.MonitoredEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service for monitoring Docker containers and system processes.
 * Dynamically schedules status checks and summary reports using TaskScheduler.
 */
@Service
public class WatchdogService implements SchedulingConfigurer{
    private final EmailService emailService;
    private final WatchdogProperties properties;
    private final TaskScheduler taskScheduler;
    private final List<MonitoredEntity> entities = new ArrayList<>();
    private final DockerClient dockerClient;
    private final Set<String> alertedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());  // ConcurrentHashMap ensures safe concurrent access without explicit synchronization.
    private static final Logger logger = LoggerFactory.getLogger(WatchdogService.class);
    private ScheduledFuture<?> checkTask;
    private ScheduledFuture<?> summaryTask;
    /**
     * Constructs the WatchdogService with required dependencies.
     * Initializes the Docker client and schedules initial tasks.
     *
     * @param emailService Service for sending emails
     * @param properties Configuration properties
     * @param taskScheduler Scheduler for managing tasks
     */
    public WatchdogService(EmailService emailService, WatchdogProperties properties, TaskScheduler taskScheduler) {
        this.emailService = emailService;
        this.dockerClient = DockerClientBuilder.getInstance().build();
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        logger.info("WatchdogService initialized with no static entities");
        rescheduleTasks();
    }
    /**
     * Configures the Spring scheduler to use the provided TaskScheduler.
     * Required for dynamic scheduling via SchedulingConfigurer.
     *
     */

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler);
    }
    /**
     * Reschedules the checkStatus and sendSummary tasks with current property values.
     * Cancels existing tasks before scheduling new ones to apply runtime updates.
     */

    public void rescheduleTasks(){
        if(checkTask!= null){
            checkTask.cancel(false); // Cancel existing task, false = don't interrupt if running
        }
        long interval = Long.parseLong(properties.getCheckInterval()); // Get check interval in milliseconds
        checkTask = taskScheduler.scheduleAtFixedRate(this::checkStatus, interval); // Schedule periodic checks


        if(summaryTask!= null){
             summaryTask.cancel(false);

        }
        summaryTask = taskScheduler.schedule(this::sendSummary, new CronTrigger(properties.getSummaryInterval()));
    }

    public void addEntity(MonitoredEntity entity) {
        boolean exists = entities.stream().anyMatch(e ->
                (entity.isDocker() && e.isDocker() && entity.getName().equals(e.getName())) ||
                        (!entity.isDocker() && !e.isDocker() && entity.getPid().equals(e.getPid()))
        );
        if (exists) throw new IllegalArgumentException("Entity already exists");
        entities.add(entity);
    }

    /**
     * Retrieves a copy of the current list of monitored entities.
     *
     * @return a new List containing all monitored entities added via API
     */
    public List<MonitoredEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Periodically checks the status of all monitored entities.
     * Runs at intervals defined by checkInterval.
     */
    @Scheduled(fixedRateString = "${watchdog.check-interval:600000}")
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
     * Checks the status of a Docker container entity and sends an email if it stops.
     *
     * @param entity the MonitoredEntity representing a Docker container
     */
    private void checkDockerContainer(MonitoredEntity entity) {
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            boolean isRunning = containers.stream()
                    .anyMatch(c -> c.getNames()[0].contains(entity.getName()) && "running".equals(c.getState()));
            if (!isRunning && !alertedEntities.contains(entity.getName())) {
                entity.setActive(false);
                entity.setLastActiveTimestamp(LocalDateTime.now().toString());
                logger.warn("Container {} stopped at {}", entity.getName(), entity.getLastActiveTimestamp());
                emailService.sendEmail("Container Down: " + entity.getName(),
                        "Container " + entity.getName() + " stopped at " + entity.getLastActiveTimestamp());
                alertedEntities.add(entity.getName());
            } else if (isRunning) {
                entity.setActive(true);
                alertedEntities.remove(entity.getName());
            }
        } catch (Exception e) {
            logger.error("Failed to check Docker container {}: {}", entity.getName(), e.getMessage(), e);
            emailService.sendEmail("Watchdog Error", "Error checking container " + entity.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Checks the status of a system process entity and sends an email if it stops.
     *
     * @param entity the MonitoredEntity representing a system process
     */
    private void checkSystemProcess(MonitoredEntity entity) {
        try {
            ProcessHandle.of(entity.getPid()).ifPresentOrElse(
                    handle -> entity.setActive(handle.isAlive()),
                    () -> {
                        entity.setActive(false);
                        if (!alertedEntities.contains(entity.getPid().toString())) {
                            entity.setLastActiveTimestamp(LocalDateTime.now().toString());
                            logger.warn("Process PID {} stopped at {}", entity.getPid(), entity.getLastActiveTimestamp());
                            emailService.sendEmail("Process Down: PID " + entity.getPid(),
                                    "Process " + entity.getPid() + " stopped at " + entity.getLastActiveTimestamp());
                            alertedEntities.add(entity.getPid().toString());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error checking process PID {}: {}", entity.getPid(), e.getMessage(), e);
            emailService.sendEmail("Watchdog Error", "Error checking process PID " + entity.getPid() + ": " + e.getMessage());
        }
    }

    /**
     * Sends a weekly summary email of all monitored entities' status.
     * Runs every Sunday at 19:14 (7:14 PM).
     */
    @Scheduled(cron = "0 14 19 * * SUN")
    public void sendSummary() {
        StringBuilder summary = new StringBuilder("Weekly Summary:\n");
        summary.append("====================\n\n");
        if (entities.isEmpty()) {
            summary.append("No monitored entities found.\n");
        } else {
            summary.append("Monitored Entities:\n");
            for (MonitoredEntity entity : entities) {
                summary.append(String.format("%s: %s\n",
                        entity.isDocker() ? "Docker Container : " + entity.getName() : "System Process : " + entity.getPid(),
                        entity.isActive() ? "Active" : "Inactive"));
            }
            summary.append("\n====================\n");
            summary.append("Generated on: ").append(new java.util.Date());
            emailService.sendEmail("Weekly Watchdog Summary", summary.toString());
        }
    }
}