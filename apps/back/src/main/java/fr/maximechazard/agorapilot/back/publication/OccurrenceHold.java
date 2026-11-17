package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Dit si une diffusion est <em>retenue</em> par une suspension de canal.
 * <p>
 * Quand un canal est suspendu, l'ordonnanceur laisse ses livraisons dues en
 * {@code PENDING}, sans appel distant. Passé son heure, une telle diffusion
 * reste {@code SCHEDULED} jusqu'à la levée de la suspension (elle part alors) ou
 * jusqu'au retard maximal (elle passe en échec). Dans l'intervalle elle n'est
 * plus « à venir », mais elle n'est pas non plus en cours : rien ne part tant
 * que le canal est suspendu. On peut donc encore la déplacer ou l'annuler sans
 * risque de course avec l'ordonnanceur.
 * <p>
 * Ce n'est pas un statut stocké : l'état se déduit de la suspension, et
 * disparaît avec elle.
 */
@Component
@RequiredArgsConstructor
public class OccurrenceHold {

    private final ChannelSuspensionService channelSuspensionService;
    private final Clock clock;

    /**
     * Programmée, son heure passée, aucun canal encore servi, et au moins un
     * canal en attente sur un canal suspendu.
     */
    public boolean isHeld(PublicationOccurrence occurrence) {
        if (occurrence.getStatus() != PublicationOccurrenceStatus.SCHEDULED
                || occurrence.getScheduledAt().isAfter(LocalDateTime.now(clock))) {
            return false;
        }

        boolean untouched = occurrence.getDeliveries().stream()
                .allMatch(delivery -> delivery.getStatus() == DeliveryStatus.PENDING);

        return untouched && occurrence.getDeliveries().stream()
                .anyMatch(delivery -> channelSuspensionService.find(delivery.getChannel()).isPresent());
    }
}
