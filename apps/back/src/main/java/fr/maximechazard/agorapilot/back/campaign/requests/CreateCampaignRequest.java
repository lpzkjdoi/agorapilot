package fr.maximechazard.agorapilot.back.campaign.requests;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Corps de {@code POST /api/campaigns}.
 * <p>
 * Les dates sont contrôlées <strong>à la journée</strong> : le front envoie
 * minuit ({@code AAAA-MM-JJT00:00:00}), qu'un {@code @FutureOrPresent} sur
 * l'instant jugeait déjà passé — une campagne ne pouvait pas démarrer le jour
 * même.
 */
@Getter
@SuppressWarnings("unused")
public class CreateCampaignRequest {
    @NotBlank
    private String name;

    private String description;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @AssertTrue(message = "La date de début ne peut pas être passée")
    boolean isStartDateNotPast() {
        return startDate == null || !startDate.toLocalDate().isBefore(LocalDate.now());
    }

    @AssertTrue(message = "La date de fin ne peut pas être passée")
    boolean isEndDateNotPast() {
        return endDate == null || !endDate.toLocalDate().isBefore(LocalDate.now());
    }

    @AssertTrue(message = "La date de fin doit suivre la date de début")
    boolean isPeriodOrdered() {
        return CampaignPeriod.isOrdered(startDate, endDate);
    }
}
