package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un canal que le distant a bloqué : tant que la ligne existe, plus aucun
 * appel n'y part.
 * <p>
 * Persistée plutôt que gardée en mémoire : un compte bloqué le reste après un
 * redémarrage du back, et c'est précisément au redémarrage que l'ordonnanceur
 * retenterait tout ce qui est en attente. Une ligne au plus par canal, d'où le
 * canal comme clé.
 */
@Entity
@Table(name = "channel_suspensions")
@Data
@NoArgsConstructor
public class ChannelSuspension {

    @Id
    @Enumerated(EnumType.STRING)
    private DeliveryChannel channel;

    /** Le refus du distant qui a déclenché la suspension, tel qu'affiché à l'utilisateur. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime suspendedAt;

    public ChannelSuspension(DeliveryChannel channel, String reason, LocalDateTime suspendedAt) {
        this.channel = channel;
        this.reason = reason;
        this.suspendedAt = suspendedAt;
    }
}
