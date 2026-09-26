package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePublicationDeliveryRequest {
    @NotNull
    private DeliveryChannel channel;
}
