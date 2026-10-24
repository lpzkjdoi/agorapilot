package fr.maximechazard.agorapilot.back.campaign.requests;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDateTime;

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

}
