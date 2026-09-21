package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@SuppressWarnings("unused")
public class CreatePublicationOccurrenceRequest {
    @NotNull
    private Long publicationId;

    /**
     * Une occurrence datée du passé ne serait jamais « planifiée » : l'ordonnanceur
     * la diffuserait au balayage suivant, sans que l'appelant l'ait demandé. Pour
     * publier tout de suite, c'est {@code POST /api/publications/{id}/deliveries}.
     */
    @NotNull
    @Future
    private LocalDateTime scheduledAt;

    @NotNull
    @NotEmpty
    private Set<DeliveryChannel> channels;
}
