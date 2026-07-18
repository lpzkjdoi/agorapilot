package fr.maximechazard.agorapilot.back.publisher.facebook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookTokenScheduler {

    private final FacebookTokenService tokenService;

    private volatile LocalDateTime lastCheckAt;
    private volatile String lastError;

    @Scheduled(cron = "0 0 3 * * *") // Each day at 3am
    public void checkAndRenew() {
        lastCheckAt = LocalDateTime.now();
        try {
            tokenService.renewIfNeeded();
            lastError = null;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("Error while renewing the token: ", e);
        }
    }

    public LocalDateTime getLastCheckAt() {
        return lastCheckAt;
    }

    public String getLastError() {
        return lastError;
    }
}
