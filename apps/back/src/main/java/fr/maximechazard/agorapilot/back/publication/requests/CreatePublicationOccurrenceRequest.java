package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@SuppressWarnings("unused")
public class CreatePublicationOccurrenceRequest {
    @NotNull
    private Long publicationId;

    @NotNull
    private LocalDateTime scheduledAt;

    @NotNull
    private Set<DeliveryChannel> channels;
}
