package fr.maximechazard.agorapilot.back.account.events;

import java.time.LocalDateTime;

public record SecurityEventDTO(Long id, LocalDateTime occurredAt, SecurityEventType type,
                               String actor, String target, String details) {

    static SecurityEventDTO of(SecurityEvent event) {
        return new SecurityEventDTO(event.getId(), event.getOccurredAt(), event.getType(),
                event.getActorUsername(), event.getTargetUsername(), event.getDetails());
    }
}
