package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Programmation d'une diffusion, sous l'une de deux formes exclusives :
 * <ul>
 *   <li>{@code date} seule — le cas courant : l'heure est choisie par le back,
 *   dans la fenêtre de publication du jour, en répartition uniforme ;</li>
 *   <li>{@code scheduledAt} — une heure précise, épinglée : elle ne bougera pas
 *   quand les autres diffusions du jour seront réparties.</li>
 * </ul>
 */
@Getter
@SuppressWarnings("unused")
public class CreatePublicationOccurrenceRequest {
    @NotNull
    private Long publicationId;

    private LocalDate date;

    /**
     * Une occurrence datée du passé ne serait jamais « planifiée » : l'ordonnanceur
     * la diffuserait au balayage suivant, sans que l'appelant l'ait demandé. Pour
     * publier tout de suite, c'est {@code POST /api/publications/{id}/deliveries}.
     */
    @Future
    private LocalDateTime scheduledAt;

    @NotNull
    @NotEmpty
    private Set<DeliveryChannel> channels;

    @AssertTrue(message = "Renseigner soit date (heure automatique), soit scheduledAt (heure fixe), pas les deux.")
    public boolean isDateXorScheduledAt() {
        return (date == null) != (scheduledAt == null);
    }
}
