# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application written in Java, designed as a cloud picture platform. The project is minimal at this stage with only the basic Spring Boot structure in place.

## Key Files and Structure

- `pom.xml`: Maven configuration file defining dependencies and build settings
- `src/main/java/com/cn/cloudpictureplatform/CloudPicturePlatformApplication.java`: Main application entry point
- `src/main/resources/application.yml`: Application configuration file
- `src/test/java/com/cn/cloudpictureplatform/CloudPicturePlatformApplicationTests.java`: Basic test file

## Development Commands

### Building
```bash
./mvnw clean package
```

### Running
```bash
./mvnw spring-boot:run
```

### Testing
```bash
./mvnw test
```

To run a single test:
```bash
./mvnw test -Dtest=CloudPicturePlatformApplicationTests
```

### Code Quality
```bash
./mvnw compile
./mvnw checkstyle:check
```

## Architecture Notes

This is a Spring Boot 4.0.1 application using Java 21. The current implementation is very basic with only a main application class and minimal configuration. The application is structured around the standard Spring Boot convention where:

1. The main application class is in `com.cn.cloudpictureplatform` package
2. Configuration is managed through `application.yml`
3. Tests follow Spring Boot testing conventions
4. Dependencies are managed through Maven's `pom.xml`

No specific business logic or features have been implemented yet. This appears to be a starter project for a cloud picture platform that will likely need additional modules for handling image storage, processing, and delivery functionality.