package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationDeliveryMapper;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationOccurrenceMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publisher.Publisher;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Couvre la planification d'une diffusion et la résolution du statut d'une
 * occurrence — les deux gestes sur lesquels repose l'ordonnanceur.
 */
@ExtendWith(MockitoExtension.class)
class PublicationOccurrenceServiceTest {

    private static final long PUBLICATION_ID = 7L;
    private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Mock
    private PublicationRepository publicationRepository;
    @Mock
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Mock
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Mock
    private PublisherRegistry publisherRegistry;
    @Mock
    private Publisher publisher;

    private PublicationOccurrenceService service;

    @BeforeEach
    void setUp() {
        service = new PublicationOccurrenceService(
                publicationRepository,
                publicationOccurrenceRepository,
                publicationDeliveryRepository,
                publisherRegistry,
                new PublicationOccurrenceMapper(new PublicationMapper(new MediaMapper()), new PublicationDeliveryMapper())
        );
    }

    private Publication publication() {
        Publication publication = new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED);
        publication.setId(PUBLICATION_ID);
        return publication;
    }

    /** La requête n'a pas de setters : elle est peuplée comme le ferait Jackson. */
    private static CreatePublicationOccurrenceRequest request(LocalDateTime scheduledAt, Set<DeliveryChannel> channels) {
        CreatePublicationOccurrenceRequest request = new CreatePublicationOccurrenceRequest();
        set(request, "publicationId", PUBLICATION_ID);
        set(request, "scheduledAt", scheduledAt);
        set(request, "channels", channels);
        return request;
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private PublicationDelivery delivery(PublicationOccurrence occurrence, DeliveryStatus status) {
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(status);
        delivery.setOccurrence(occurrence);
        return delivery;
    }

    @Nested
    class Create {

        @Test
        void schedules_an_occurrence_with_one_pending_delivery_per_channel() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication()));
            when(publisherRegistry.forChannel(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(publisher));
            when(publicationOccurrenceRepository.save(any(PublicationOccurrence.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PublicationOccurrenceDTO dto = service.create(request(SCHEDULED_AT, Set.of(DeliveryChannel.FACEBOOK)));

            ArgumentCaptor<PublicationOccurrence> captor = ArgumentCaptor.forClass(PublicationOccurrence.class);
            verify(publicationOccurrenceRepository).save(captor.capture());

            PublicationOccurrence saved = captor.getValue();
            assertThat(saved.getScheduledAt()).isEqualTo(SCHEDULED_AT);
            assertThat(saved.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
            assertThat(saved.getPublication().getId()).isEqualTo(PUBLICATION_ID);
            assertThat(saved.getDeliveries()).singleElement().satisfies(delivery -> {
                assertThat(delivery.getChannel()).isEqualTo(DeliveryChannel.FACEBOOK);
                assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
                assertThat(delivery.getOccurrence()).isSameAs(saved);
            });

            assertThat(dto.scheduledAt()).isEqualTo(SCHEDULED_AT);
            assertThat(dto.status()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
            assertThat(dto.deliveries()).singleElement()
                    .satisfies(delivery -> assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING));
        }

        /** Rien n'est diffusé à la planification : c'est l'ordonnanceur qui sert. */
        @Test
        void publishes_nothing_at_scheduling_time() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication()));
            when(publisherRegistry.forChannel(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(publisher));
            when(publicationOccurrenceRepository.save(any(PublicationOccurrence.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.create(request(SCHEDULED_AT, Set.of(DeliveryChannel.FACEBOOK)));

            verifyNoInteractions(publisher);
        }

        @Test
        void rejects_an_unknown_publication() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request(SCHEDULED_AT, Set.of(DeliveryChannel.FACEBOOK))))
                    .isInstanceOf(PublicationNotFoundException.class);

            verify(publicationOccurrenceRepository, never()).save(any());
        }

        /**
         * Mieux vaut refuser tout de suite que laisser dormir une occurrence qui
         * échouera dans trois jours, faute de publisher pour son canal.
         */
        @Test
        void rejects_a_channel_without_publisher() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication()));
            when(publisherRegistry.forChannel(DeliveryChannel.INTRAMUROS)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request(SCHEDULED_AT, Set.of(DeliveryChannel.INTRAMUROS))))
                    .isInstanceOf(UnsupportedDeliveryChannelException.class)
                    .hasMessageContaining("INTRAMUROS");

            verify(publicationOccurrenceRepository, never()).save(any());
        }
    }

    @Nested
    class RefreshStatus {

        private PublicationOccurrence occurrence() {
            PublicationOccurrence occurrence = new PublicationOccurrence();
            occurrence.setId(42L);
            occurrence.setScheduledAt(SCHEDULED_AT);
            return occurrence;
        }

        @Test
        void marks_the_occurrence_published_once_every_channel_is_served() {
            PublicationOccurrence occurrence = occurrence();
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                    .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED)));

            service.refreshStatus(occurrence);

            assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.PUBLISHED);
            verify(publicationOccurrenceRepository).save(occurrence);
        }

        /**
         * Sortir l'occurrence de la file est le point clé : restée SCHEDULED,
         * l'ordonnanceur la reprendrait à chaque balayage sans jamais rien servir.
         */
        @Test
        void marks_the_occurrence_failed_when_a_channel_failed() {
            PublicationOccurrence occurrence = occurrence();
            PublicationDelivery failed = delivery(occurrence, DeliveryStatus.FAILED);
            failed.setChannel(DeliveryChannel.INTRAMUROS);
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                    .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED), failed));

            service.refreshStatus(occurrence);

            assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.FAILED);
            verify(publicationOccurrenceRepository).save(occurrence);
        }

        @Test
        void leaves_the_occurrence_scheduled_while_a_channel_is_still_pending() {
            PublicationOccurrence occurrence = occurrence();
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            pending.setChannel(DeliveryChannel.INTRAMUROS);
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                    .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED), pending));

            service.refreshStatus(occurrence);

            assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
            verify(publicationOccurrenceRepository, never()).save(occurrence);
        }

        /**
         * Une livraison IN_PROGRESS n'est pas tranchée : l'occurrence doit rester
         * dans la file pour que le balayage suivant la solde si elle est bloquée.
         */
        @Test
        void leaves_the_occurrence_scheduled_while_a_channel_is_in_progress() {
            PublicationOccurrence occurrence = occurrence();
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                    .thenReturn(List.of(delivery(occurrence, DeliveryStatus.IN_PROGRESS)));

            service.refreshStatus(occurrence);

            assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
            verify(publicationOccurrenceRepository, never()).save(occurrence);
        }

        @Test
        void leaves_an_occurrence_without_delivery_untouched() {
            PublicationOccurrence occurrence = occurrence();
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence)).thenReturn(List.of());

            service.refreshStatus(occurrence);

            assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
            verify(publicationOccurrenceRepository, never()).save(occurrence);
        }

        @Test
        void writes_nothing_when_the_status_already_matches() {
            PublicationOccurrence occurrence = occurrence();
            occurrence.setStatus(PublicationOccurrenceStatus.PUBLISHED);
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                    .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED)));

            service.refreshStatus(occurrence);

            verify(publicationOccurrenceRepository, never()).save(occurrence);
        }
    }

    @Nested
    class Weekly {

        private PublicationOccurrence occurrenceOn(LocalDateTime scheduledAt) {
            PublicationOccurrence occurrence = new PublicationOccurrence();
            occurrence.setId(42L);
            occurrence.setScheduledAt(scheduledAt);
            occurrence.setPublication(publication());
            return occurrence;
        }

        /**
         * Régression : la requête s'arrêtait à minuit du septième jour, qui
         * restait donc toujours vide alors que la carte l'annonçait.
         */
        @Test
        void queries_seven_full_days_from_today() {
            LocalDate today = LocalDate.now();
            when(publicationOccurrenceRepository.findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(any(), any()))
                    .thenReturn(List.of());

            service.getWeeklyOccurrences();

            verify(publicationOccurrenceRepository).findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                    today.atStartOfDay(), today.plusDays(7).atStartOfDay());
        }

        @Test
        void files_an_occurrence_of_the_seventh_day_under_that_day() {
            LocalDate today = LocalDate.now();
            LocalDateTime lastDayEvening = today.plusDays(6).atTime(18, 30);
            when(publicationOccurrenceRepository.findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(any(), any()))
                    .thenReturn(List.of(occurrenceOn(lastDayEvening)));

            Map<String, List<PublicationOccurrenceDTO>> week = service.getWeeklyOccurrences();

            assertThat(week).hasSize(7);
            assertThat(week.get(today.plusDays(6).atStartOfDay().toString()))
                    .singleElement()
                    .satisfies(dto -> assertThat(dto.scheduledAt()).isEqualTo(lastDayEvening));
            assertThat(week.get(today.atStartOfDay().toString())).isEmpty();
        }
    }
}
