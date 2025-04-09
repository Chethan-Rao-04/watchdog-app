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

## Encryption ( Email sender password)
Utilise config/EncryptPassword.java


Edit `application.yml`:
- `watchdog.check-interval`: Monitoring frequency (ms).
- `summary-time`: Cron notation for Weekly summary time (eq- "0 0 11 * * WED")
- `spring.mail`: SMTP settings for email. (username,encrypted password, recipients, host, port)

## Environment variables
-  `export JASYPT_ENCRYPTOR_PASSWORD=watchdog123`            
  

## Running
1. Build: `mvn clean install`
2. Run: `mvn spring-boot:run`



## Docker Container Monitoring

### Start a Docker Container
docker run --name test-container -d nginx

### Add it to monitoring:

curl -X POST "http://localhost:8080/watchdog/entities" \
-H "Content-Type: application/json" \
-d '{"name":"test-container","docker":true,"active":true}'

### Trigger an alert:
docker stop test-container



### Start a test process:
sleep 3600 &
echo $!  # Note this PID

### Add it to monitoring:

curl -X POST "http://localhost:8080/watchdog/entities" \
-H "Content-Type: application/json" \
-d '{"pid":PROCESS_PID,"docker":false,"active":true}'

### Trigger an alert:
docker stop test-container

## List all monitored entities

curl http://localhost:8080/watchdog/entities






