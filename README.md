# Watchdog Monitoring Tool

A monitoring application for Docker containers and system processes with email alerts and weekly summaries.

## Features
- Monitors Docker containers and system processes by PID.
- Sends email alerts when an entity stops.
- Provides a weekly  summary every Sunday at 19:14.
- REST API for managing monitored entities .

## Prerequisites
- Java 21 (e.g., Amazon Corretto 21.0.5)
- Maven 3.9.9
- Docker

## Libraries Used
- **Spring Boot Starter Web** (2.7.18) - REST API and web framework.
- **Spring Boot Starter Mail** (2.7.18) - Email notification support.
- **Docker Java** (3.4.2) - Docker client for container monitoring.
- **Lombok** - Code simplification with annotations (e.g., `@Data`).

## Configuration
Edit `application.yml`:
- `watchdog.check-interval`: Monitoring frequency (ms).
- `spring.mail`: SMTP settings for email.

## Environment variables
-  `export JASYPT_ENCRYPTOR_PASSWORD=watchdog123`            
  

## Running
1. Build: `mvn clean install`
2. Run: `mvn spring-boot:run`

## Encryption

    ```java -cp jasypt-1.9.3.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \
     input="abcd efgh ijkl mnop" \
     password=your-secret-key \
     algorithm=PBEWithMD5AndDES```

    export JASYPT_ENCRYPTOR_PASSWORD="your-secret-key"

## Testing with sample data (DOCKER)
docker run --name test-colima -d nginx


docker start test-colima
docker start test-colima-stopped
docker stop test-colima-stopped



Add the process to the MonitoredEntities using API or directly via application.yml


    
# API Endpoints

# Test for Docker Container


## Start a Docker Container

docker run --name test-ab -d nginx

## Add the Docker Container to the Monitored Entities

curl -X POST "http://localhost:8080/watchdog/entities" -H "Content-Type: application/json" -d '{"name":"test-ab","docker":true,"active":true}'

## List all monitored entities

curl http://localhost:8080/watchdog/entities

# Trigger alert by stopping a Docker Container
docker stop test-ab




# Create a Simple Process
- Start a simple process and note the PID
- ```sleep 3600 &```

#  Add the new System Process to the Monitored Entities 
- `POST Method ` - ```  curl -X POST "http://localhost:8080/watchdog/entities" \
  -H "Content-Type: application/json" \
  -d '{"pid":97212,"docker":false,"active":true}'  ``` \

# STOP the running entities
docker stop test-conta





