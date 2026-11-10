package fr.maximechazard.agorapilot.back.campaign.requests;

import java.time.LocalDateTime;

/** Règle commune à la création et à la modification d'une campagne. */
final class CampaignPeriod {

    private CampaignPeriod() {
    }

    /** Une période ouverte d'un côté ou des deux est toujours ordonnée. */
    static boolean isOrdered(LocalDateTime startDate, LocalDateTime endDate) {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
