package fr.maximechazard.agorapilot.back.campaign.requests;

import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@SuppressWarnings("unused")
public class CreateCampaignRequest {
    @NotBlank
    private String name;

    private String description;

    @FutureOrPresent
    private LocalDateTime startDate;

    @Future
    private LocalDateTime endDate;

    private List<CreatePublicationRequest> publications;
}
