package fr.maximechazard.agorapilot.back.dashboard;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;

import java.util.List;

/**
 * Synthèse affichée par le tableau de bord : quatre compteurs et les prochaines
 * diffusions.
 *
 * @param activeCampaigns    campagnes en cours (commencées, ni finies ni clôturées)
 * @param scheduledThisWeek  diffusions encore programmées sur les sept jours du calendrier de la semaine
 * @param draftPublications  publications en brouillon, à vérifier avant diffusion
 * @param failedOccurrences  diffusions en échec, en attente d'une reprise
 * @param upcomingOccurrences les cinq prochaines diffusions programmées, la plus proche d'abord
 */
public record DashboardDTO(
        long activeCampaigns,
        long scheduledThisWeek,
        long draftPublications,
        long failedOccurrences,
        List<PublicationOccurrenceDTO> upcomingOccurrences
) {
}
