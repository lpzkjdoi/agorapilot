package fr.maximechazard.agorapilot.back.campaign.requests;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Corps de {@code PUT /api/campaigns/{id}} : remplace nom, description et
 * période.
 * <p>
 * Contrairement à la création, aucune date n'est tenue d'être future : une
 * campagne en cours a une date de début passée, et la modifier ne doit pas
 * obliger à la déplacer.
 */
@Getter
@SuppressWarnings("unused")
public class UpdateCampaignRequest {
    @NotBlank
    private String name;

    private String description;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @AssertTrue(message = "La date de fin doit suivre la date de début")
    boolean isPeriodOrdered() {
        return CampaignPeriod.isOrdered(startDate, endDate);
    }
}
