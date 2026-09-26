package fr.maximechazard.agorapilot.back.publication.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Nouveau jour d'une diffusion, et éventuellement son heure — pour la déplacer
 * ({@code PUT /{id}/schedule}) ou la reprendre après un échec ({@code POST /{id}/retry}).
 * <p>
 * Sans {@code time}, la diffusion est (ou redevient) automatique : le back la
 * place dans la fenêtre de publication du jour. Avec {@code time}, elle est
 * épinglée à cette heure. Déplacer une diffusion épinglée vers un autre jour en
 * gardant son heure revient donc à renvoyer la même {@code time}.
 */
@Getter
@SuppressWarnings("unused")
public class RescheduleOccurrenceRequest {
    @NotNull
    private LocalDate date;

    private LocalTime time;
}
