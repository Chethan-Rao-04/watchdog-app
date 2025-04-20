# Watchdog Monitoring Tool

A monitoring application for Docker containers and system processes with email alerts and weekly summaries.

## Features
- **Continuous Monitoring**: Tracks Docker containers and system processes by their PID
- **Instant Alerts**: Sends immediate email notifications when monitored entities stop
- **Status Reporting**: Provides comprehensive weekly summary reports every Sunday
- **Management API**: RESTful endpoints for managing monitored entities
- **Secure Configuration**: Jasypt encryption support for sensitive credentials
- 
## Prerequisites
- Java 17
- Maven 3.9.x
- Docker (for container monitoring)
- SMTP server access (for notifications)

## Technical Stack

- **Spring Boot**: Version 2.7.18
- **Docker Java Client**: Version 3.4.2 for container monitoring
- **Spring Mail**: For sending email notifications
- **Jasypt**: For encrypting sensitive configuration values
- **Lombok**: For reducing boilerplate code
-

## Configuration

### Application Settings

Edit `application.yml`:
- `watchdog.check-interval`: Monitoring frequency (ms).
- `summary-time`: Cron notation for Weekly summary time (eq- "0 0 11 * * WED")
- `spring.mail`: SMTP settings for email. (username, recipients, host, port)


## Running
- Note: Make sure you run the application in the same terminal
  after setting the environment variables.

### Set Environment variables
-  ` export JASYPT_ENCRYPTOR_PASSWORD=watchdog123 `
-  ` export MAIL_PASSWORD=yourmailpassword `

### Build and Run
- Build: ` mvn clean install   `
- Run: ` mvn spring-boot:run  `


## Testing

###  Docker Container Monitoring

1. Start a Docker Container
`   docker run --name test-container -d nginx   `

2. Add it to monitoring:

`  curl -X POST "http://localhost:8080/watchdog/entities" \
-H "Content-Type: application/json" \
-d '{"name":"test-container122","docker":true,"active":true}'  `

3. Trigger an alert:
`docker stop test-container`

###  System Process Monitoring

1. Start a test process:
` sleep 3600 & `   # Note this PID

2. Add it to monitoring:

`  curl -X POST "http://localhost:8080/watchdog/entities" \
-H "Content-Type: application/json" \
-d '{"pid":55881,"docker":false,"active":true}'  `

3. Trigger an alert:
` docker stop test-container `

### Weekly Summary

1. Set ` summary-time ` in ` application.yml ` to a time that is close to the current time.

### List all monitored entities

` curl http://localhost:8080/watchdog/entities `


## Delete a monitored entity

### Docker Container

`  curl -X DELETE "http://localhost:8080/watchdog/entities/test-container"  `

### System Process

`  curl -X DELETE "http://localhost:8080/watchdog/entities/12345"  `





