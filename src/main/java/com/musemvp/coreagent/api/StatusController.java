package com.musemvp.coreagent.api;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final String applicationName;

    public StatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/status")
    public ApplicationStatus status() {
        return new ApplicationStatus(
                applicationName,
                "UP",
                "Spring Boot application is running",
                Instant.now());
    }

    public record ApplicationStatus(String application, String status, String message, Instant timestamp) {
    }
}
