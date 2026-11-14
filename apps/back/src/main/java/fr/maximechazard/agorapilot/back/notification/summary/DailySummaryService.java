package fr.maximechazard.agorapilot.back.notification.summary;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationLinks;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationTexts;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compose le programme d'un jour : les diffusions prévues, et les échecs de la
 * veille pour que rien ne passe inaperçu.
 * <p>
 * Il part même les jours sans diffusion : son absence signale alors que le
 * back, ou ses notifications, ne tournent plus.
 */
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final PublicationOccurrenceRepository occurrenceRepository;
    private final NotificationLinks links;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Notification today() {
        return forDay(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public Notification forDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        List<PublicationOccurrence> occurrences = occurrenceRepository
                .findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(start, end).stream()
                .filter(occurrence -> !isArchived(occurrence.getPublication()))
                .sorted(Comparator.comparing(PublicationOccurrence::getScheduledAt))
                .toList();

        long failedYesterday = occurrenceRepository.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                PublicationOccurrenceStatus.FAILED, day.minusDays(1).atStartOfDay(), start);

        String channels = occurrences.stream()
                .flatMap(occurrence -> occurrence.getDeliveries().stream())
                .map(PublicationDelivery::getChannel)
                .distinct()
                .sorted()
                .map(NotificationTexts::channelLabel)
                .collect(Collectors.joining(", "));

        Notification.Builder notification = Notification
                .builder(NotificationType.DAILY_SUMMARY,
                        failedYesterday > 0 ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                        "Programme du " + DAY.format(day))
                .summary(occurrences.isEmpty()
                        ? "Aucune diffusion prévue aujourd'hui."
                        : NotificationTexts.plural(occurrences.size(), "diffusion prévue", "diffusions prévues")
                                + " aujourd'hui.")
                .link(links.calendar())
                .occurredAt(clock.instant());

        occurrences.forEach(occurrence -> notification.entry(entry(occurrence)));

        return notification
                .field(NotificationField.inline("Prévues", String.valueOf(occurrences.size())))
                .field(NotificationField.inline("Canaux", channels.isEmpty() ? "—" : channels))
                .field(NotificationField.inline("Échecs hier", String.valueOf(failedYesterday)))
                .build();
    }

    private NotificationEntry entry(PublicationOccurrence occurrence) {
        Publication publication = occurrence.getPublication();

        String channels = occurrence.getDeliveries().stream()
                .map(PublicationDelivery::getChannel)
                .distinct()
                .sorted()
                .map(NotificationTexts::channelLabel)
                .collect(Collectors.joining(", "));

        String campaign = publication.getCampaign() == null ? null : "Campagne " + publication.getCampaign().getName();
        String status = switch (occurrence.getStatus()) {
            case SCHEDULED -> null;
            case PUBLISHED -> "déjà diffusée";
            case FAILED -> "en échec";
        };

        String detail = Stream.of(channels.isEmpty() ? null : channels, campaign, status)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" · "));

        return new NotificationEntry(
                occurrence.getScheduledAt().atZone(clock.getZone()).toInstant(),
                NotificationTexts.excerpt(publication.getContent()),
                detail,
                links.calendar()
        );
    }

    private static boolean isArchived(Publication publication) {
        return publication == null || Boolean.TRUE.equals(publication.getArchived());
    }
}
