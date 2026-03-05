package fr.maximechazard.agorapilot.back.publishing.channel.facebook;

import fr.maximechazard.agorapilot.back.service.FacebookTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookTokenScheduler {

    private final FacebookTokenService tokenService;

    @Scheduled(cron = "0 0 3 * * *") // Each day at 3am
    public void checkAndRenew() {
        try {
            tokenService.renewIfNeeded();
        } catch (Exception e) {
            log.error("Error while renewing the token: ", e);
        }
    }
}