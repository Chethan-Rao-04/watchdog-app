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

## Running
1. Build: `mvn clean install`
2. Run: `mvn spring-boot:run`

## Testing with sample data (DOCKER)
docker run --name test-colima -d nginx
docker run --name test-colima-stopped -d busybox sleep 3600

These entites are added to Monitored Entities List using the API or directly via application.yml

docker start test-colima
docker start test-colima-stopped
docker stop test-colima-stopped



Add the process to the MonitoredEntities using API or directly via application.yml


    
## API Endpoints

# Add New Entity 
- `POST /watchdog/entities` - Add an entity (e.g., `{"name":"test","docker":true,"active":true}`).

# Start a Docker Container
docker run --name test-container-test -d nginx  
colima start test-container-running



# A Add the Docker Container to the Monitored Entities

- `POST Method ` - ```  curl -X POST "http://localhost:8080/watchdog/entities" \
  -H "Content-Type: application/json" \
  -d '{"name":"test-container-test","docker":true,"active":true}'  ``` \


# Create a Simple Process
- Start a simple process-   ```sleep 3600 &```
- To fetch the PID - ```echo $!```
- To get details -   ```ps -p 15801  ```  NOTE: Replace with actual PID


#  Add the new System Process to the Monitored Entities 
- `POST Method ` - ```  curl -X POST "http://localhost:8080/watchdog/entities" \
  -H "Content-Type: application/json" \
  -d '{"pid":24498,"docker":false,"active":true}'  ``` \



# List all monitored entities
- `GET Method Example` - ```  curl http://localhost:8080/watchdog/entities  ```

curl -X POST "http://localhost:8080/watchdog/entities" \
-H "Content-Type: application/json" \
-d '{"name":myContainer123,"docker":true,"active":true}'
