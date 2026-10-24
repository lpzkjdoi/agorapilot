package fr.maximechazard.agorapilot.back.dashboard;

import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationOccurrenceMapper;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardService {
    /** Même période que le calendrier de la semaine : aujourd'hui et les six jours suivants. */
    private static final int WEEK_LENGTH = 7;

    private final CampaignRepository campaignRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationOccurrenceMapper publicationOccurrenceMapper;
    private final Clock clock;

    /**
     * Les « prochaines » diffusions partent de maintenant : une diffusion dont
     * l'heure est passée mais qui reste {@code SCHEDULED} (canal suspendu,
     * ordonnanceur en retard) n'est plus à venir. Elle compte en revanche dans
     * la semaine, tant qu'elle n'est pas servie.
     */
    @Transactional(readOnly = true)
    public DashboardDTO getSummary() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        return new DashboardDTO(
                campaignRepository.countActive(now),
                publicationOccurrenceRepository.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                        PublicationOccurrenceStatus.SCHEDULED, today.atStartOfDay(), today.plusDays(WEEK_LENGTH).atStartOfDay()),
                publicationRepository.countByStatusAndArchivedFalse(PublicationStatus.DRAFT),
                publicationOccurrenceRepository.countByStatus(PublicationOccurrenceStatus.FAILED),
                publicationOccurrenceRepository
                        .findTop5ByStatusAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(PublicationOccurrenceStatus.SCHEDULED, now)
                        .stream()
                        .map(publicationOccurrenceMapper::toDTO)
                        .toList());
    }
}
