package fr.maximechazard.agorapilot.back.account.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Une ligne du journal de sécurité. Jamais modifiée ni supprimée par l'application. */
@Entity
@Table(name = "security_events")
@NoArgsConstructor
@Getter
public class SecurityEvent {
    static final int USERNAME_LENGTH = 64;
    static final int DETAILS_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SecurityEventType type;

    /** Qui a agi ; {@code null} pour une tentative de connexion anonyme. */
    @Column(length = USERNAME_LENGTH)
    private String actorUsername;

    /** Le compte visé, s'il diffère de l'acteur. */
    @Column(length = USERNAME_LENGTH)
    private String targetUsername;

    @Column(length = DETAILS_LENGTH)
    private String details;

    public SecurityEvent(LocalDateTime occurredAt, SecurityEventType type, String actorUsername,
                         String targetUsername, String details) {
        this.occurredAt = occurredAt;
        this.type = type;
        this.actorUsername = truncate(actorUsername, USERNAME_LENGTH);
        this.targetUsername = truncate(targetUsername, USERNAME_LENGTH);
        this.details = truncate(details, DETAILS_LENGTH);
    }

    /** Un pseudo tapé à la connexion peut avoir n'importe quelle longueur. */
    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
