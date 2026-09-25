package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dépile les occurrences dont l'heure de diffusion est passée.
 * <p>
 * C'est la pièce qui rend la planification réelle : sans elle, une occurrence
 * {@code SCHEDULED} attendrait indéfiniment. Le balayage est volontairement
 * naïf — une requête par minute sur les occurrences en retard — parce que
 * l'ordre de grandeur est de quelques diffusions par jour pour une collectivité.
 * <p>
 * L'ordonnanceur Spring n'exécute qu'une tâche à la fois par défaut, et
 * {@code fixedDelay} compte à partir de la <em>fin</em> du balayage précédent :
 * deux passes ne peuvent donc pas se chevaucher et servir deux fois la même
 * livraison, même si un canal distant traîne.
 * <p>
 * La dernière erreur est conservée avec son heure, sans être effacée par les
 * balayages suivants : remise à zéro chaque minute, elle ne serait jamais
 * lisible au moment où quelqu'un la consulte ({@code GET /admin/scheduler/status}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PublicationOccurrenceScheduler {

    private static final String DELAY = "${agorapilot.scheduling.occurrences-delay:PT1M}";

    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationDeliveryService publicationDeliveryService;

    private volatile LocalDateTime lastRunAt;
    private volatile LocalDateTime lastErrorAt;
    private volatile String lastError;

    @Scheduled(fixedDelayString = DELAY, initialDelayString = DELAY)
    public void publishDueOccurrences() {
        LocalDateTime now = LocalDateTime.now();
        lastRunAt = now;

        List<PublicationOccurrence> due = publicationOccurrenceRepository
                .findAllByScheduledAtBeforeAndStatus(now, PublicationOccurrenceStatus.SCHEDULED);

        if (due.isEmpty()) {
            return;
        }

        log.info("{} occurrence(s) due for delivery.", due.size());

        for (PublicationOccurrence occurrence : due) {
            // Une occurrence qui explose ne doit pas emporter le reste du lot :
            // les suivantes sont déjà en retard, elles partent quand même.
            try {
                publicationDeliveryService.publishScheduledOccurrence(occurrence.getId());
            } catch (Exception e) {
                lastErrorAt = LocalDateTime.now();
                lastError = "Occurrence " + occurrence.getId() + ": " + e.getMessage();
                log.error("Delivery of scheduled occurrence {} failed:", occurrence.getId(), e);
            }
        }
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public LocalDateTime getLastErrorAt() {
        return lastErrorAt;
    }

    public String getLastError() {
        return lastError;
    }
}
