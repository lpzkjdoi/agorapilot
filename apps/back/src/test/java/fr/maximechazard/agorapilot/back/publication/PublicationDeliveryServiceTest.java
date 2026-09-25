package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationDeliveryMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.DeliveryFailedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publisher.Publisher;
import fr.maximechazard.agorapilot.back.publisher.PublisherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationDeliveryServiceTest {

    private static final long PUBLICATION_ID = 7L;
    private static final long OCCURRENCE_ID = 42L;

    @Mock
    private PublicationRepository publicationRepository;
    @Mock
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Mock
    private PublicationDeliveryRepository publicationDeliveryRepository;
    @Mock
    private PublisherRegistry publisherRegistry;
    @Mock
    private PublicationOccurrenceService publicationOccurrenceService;
    @Mock
    private Publisher publisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PublicationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new PublicationDeliveryService(
                publicationRepository,
                publicationOccurrenceRepository,
                publicationDeliveryRepository,
                publisherRegistry,
                publicationOccurrenceService,
                new PublicationDeliveryMapper(),
                new SchedulingProperties(Duration.ofMinutes(1), Duration.ofHours(1)),
                transactionManager
        );
    }

    private Publication publication() {
        Publication publication = new Publication("Marché de Noël samedi", PublicationStatus.VERIFIED);
        publication.setId(PUBLICATION_ID);
        return publication;
    }

    /** L'occurrence telle que la renverrait le repository après sauvegarde. */
    private PublicationOccurrence savedOccurrence() {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setId(OCCURRENCE_ID);
        occurrence.setScheduledAt(LocalDateTime.now());
        occurrence.setPublication(publication());
        return occurrence;
    }

    private PublicationDelivery delivery(PublicationOccurrence occurrence, DeliveryStatus status) {
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setId(99L);
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(status);
        delivery.setOccurrence(occurrence);
        if (status == DeliveryStatus.PUBLISHED) {
            delivery.setPublishedAt(LocalDateTime.now());
            delivery.setExternalId("123_456");
        }
        return delivery;
    }

    @Nested
    class PublishNow {

        private PublicationOccurrence stubSuccessfulPublication(DeliveryStatus resultingStatus) {
            PublicationOccurrence occurrence = savedOccurrence();

            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication()));
            when(publisherRegistry.forChannel(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(publisher));
            when(publicationOccurrenceRepository.save(any(PublicationOccurrence.class))).thenReturn(occurrence);
            when(publicationDeliveryRepository.findByOccurrenceAndChannel(occurrence, DeliveryChannel.FACEBOOK))
                    .thenReturn(Optional.of(delivery(occurrence, resultingStatus)));

            return occurrence;
        }

        @Test
        void creates_an_immediate_occurrence_carrying_the_requested_channel() {
            stubSuccessfulPublication(DeliveryStatus.PUBLISHED);

            LocalDateTime before = LocalDateTime.now();
            service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

            ArgumentCaptor<PublicationOccurrence> captor = ArgumentCaptor.forClass(PublicationOccurrence.class);
            verify(publicationOccurrenceRepository).save(captor.capture());

            PublicationOccurrence created = captor.getAllValues().getFirst();
            assertThat(created.getPublication().getId()).isEqualTo(PUBLICATION_ID);
            assertThat(created.getScheduledAt()).isAfterOrEqualTo(before);
            assertThat(created.getDeliveries())
                    .singleElement()
                    .satisfies(delivery -> assertThat(delivery.getChannel()).isEqualTo(DeliveryChannel.FACEBOOK));
        }

        @Test
        void returns_the_published_delivery() {
            PublicationOccurrence occurrence = stubSuccessfulPublication(DeliveryStatus.PUBLISHED);

            PublicationDeliveryDTO dto = service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

            verify(publisher).publish(occurrence);
            assertThat(dto.status()).isEqualTo(DeliveryStatus.PUBLISHED);
            assertThat(dto.channel()).isEqualTo(DeliveryChannel.FACEBOOK);
            assertThat(dto.occurrenceId()).isEqualTo(occurrence.getId());
            assertThat(dto.externalId()).isEqualTo("123_456");
        }

        @Test
        void realigns_the_occurrence_status_once_the_channel_is_served() {
            PublicationOccurrence occurrence = stubSuccessfulPublication(DeliveryStatus.PUBLISHED);

            service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        @Test
        void reports_a_failed_delivery_without_losing_its_trace() {
            PublicationOccurrence occurrence = stubSuccessfulPublication(DeliveryStatus.FAILED);
            PublicationDelivery failed = delivery(occurrence, DeliveryStatus.FAILED);
            failed.setErrorMessage("No token available");
            when(publicationDeliveryRepository.findByOccurrenceAndChannel(occurrence, DeliveryChannel.FACEBOOK))
                    .thenReturn(Optional.of(failed));

            assertThatThrownBy(() -> service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK))
                    .isInstanceOf(DeliveryFailedException.class)
                    .hasMessage("No token available");

            // L'échec est acté sur l'occurrence avant de remonter à l'appelant :
            // sans cela elle resterait SCHEDULED et l'ordonnanceur la reprendrait.
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        @Test
        void rejects_an_unknown_publication() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK))
                    .isInstanceOf(PublicationNotFoundException.class);

            verifyNoInteractions(publisherRegistry, publicationOccurrenceRepository, publicationDeliveryRepository);
        }

        @Test
        void rejects_a_channel_without_publisher() {
            when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication()));
            when(publisherRegistry.forChannel(DeliveryChannel.INTRAMUROS)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishNow(PUBLICATION_ID, DeliveryChannel.INTRAMUROS))
                    .isInstanceOf(UnsupportedDeliveryChannelException.class);

            verifyNoInteractions(publicationOccurrenceRepository, publicationDeliveryRepository);
        }
    }

    @Nested
    class PublishScheduledOccurrence {

        /**
         * Les entités portent un {@code equals} généré : deux occurrences
         * distinctes ne se valent pas. Les tests réutilisent donc la même
         * instance du début à la fin, comme le ferait Hibernate.
         */
        private void stub(PublicationOccurrence occurrence, PublicationDelivery... deliveries) {
            when(publicationOccurrenceRepository.findById(OCCURRENCE_ID)).thenReturn(Optional.of(occurrence));
            when(publicationDeliveryRepository.findAllByOccurrence(occurrence)).thenReturn(List.of(deliveries));
        }

        /** Une livraison que la prise en charge retient, puis que la diffusion relit. */
        private void stubServed(PublicationDelivery delivery, Publisher channelPublisher) {
            when(publisherRegistry.forChannel(delivery.getChannel())).thenReturn(Optional.of(channelPublisher));
            when(publicationDeliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        }

        private PublicationOccurrence occurrenceDue(long minutesAgo) {
            PublicationOccurrence occurrence = savedOccurrence();
            occurrence.setScheduledAt(LocalDateTime.now().minusMinutes(minutesAgo));
            return occurrence;
        }

        @Test
        void serves_every_pending_channel_of_the_occurrence() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            stub(occurrence, pending);
            stubServed(pending, publisher);

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            verify(publisher).publish(occurrence);
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        /**
         * Le cœur de la protection contre la double publication : la prise en
         * charge est validée en base avant que le canal distant soit appelé. Un
         * arrêt du back pendant l'appel laisse IN_PROGRESS, jamais PENDING.
         */
        @Test
        void commits_the_claim_before_calling_the_remote_channel() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            stub(occurrence, pending);
            stubServed(pending, publisher);
            doAnswer(invocation -> {
                assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
                return null;
            }).when(publisher).publish(occurrence);

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            InOrder order = inOrder(publicationDeliveryRepository, transactionManager, publisher);
            order.verify(publicationDeliveryRepository).save(pending);
            order.verify(transactionManager).commit(any());
            order.verify(publisher).publish(occurrence);
        }

        /**
         * Le canal distant a pu accepter la publication avant de rompre : une
         * reprise automatique publierait deux fois.
         */
        @Test
        void never_retries_a_delivery_that_already_failed() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            stub(occurrence, delivery(occurrence, DeliveryStatus.FAILED));

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            verifyNoInteractions(publisherRegistry, publisher);
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        @Test
        void never_republishes_a_delivery_already_published() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            stub(occurrence, delivery(occurrence, DeliveryStatus.PUBLISHED));

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            verifyNoInteractions(publisherRegistry, publisher);
        }

        /**
         * Les balayages ne se chevauchent pas : une livraison encore IN_PROGRESS
         * vient d'une diffusion interrompue. Elle est soldée sans être rejouée.
         */
        @Test
        void settles_an_interrupted_delivery_as_failed_without_republishing() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery interrupted = delivery(occurrence, DeliveryStatus.IN_PROGRESS);
            stub(occurrence, interrupted);

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            assertThat(interrupted.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(interrupted.getErrorMessage()).contains("interrupted").contains("FACEBOOK");
            verify(publicationDeliveryRepository).save(interrupted);
            verifyNoInteractions(publisherRegistry, publisher);
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        /** Après une longue panne, une annonce d'événement passé ne doit pas partir. */
        @Test
        void does_not_publish_a_delivery_later_than_the_maximum_lateness() {
            PublicationOccurrence occurrence = occurrenceDue(61);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            stub(occurrence, pending);
            when(publisherRegistry.forChannel(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(publisher));

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(pending.getErrorMessage()).contains("60 minutes late");
            verifyNoInteractions(publisher);
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        @Test
        void still_publishes_a_delivery_within_the_maximum_lateness() {
            PublicationOccurrence occurrence = occurrenceDue(59);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            stub(occurrence, pending);
            stubServed(pending, publisher);

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            verify(publisher).publish(occurrence);
        }

        /**
         * Sans trace d'échec, la livraison resterait PENDING et l'occurrence
         * reviendrait à chaque balayage, indéfiniment.
         */
        @Test
        void traces_a_channel_without_publisher_as_failed() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            pending.setChannel(DeliveryChannel.INTRAMUROS);
            stub(occurrence, pending);
            when(publisherRegistry.forChannel(DeliveryChannel.INTRAMUROS)).thenReturn(Optional.empty());

            service.publishScheduledOccurrence(OCCURRENCE_ID);

            assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(pending.getErrorMessage()).contains("INTRAMUROS");
            verify(publicationDeliveryRepository).save(pending);
        }

        /**
         * Une exception qui échappe au publisher laisserait la livraison
         * IN_PROGRESS : elle est soldée tout de suite, avec son vrai motif, et le
         * balayage n'en souffre pas.
         */
        @Test
        void settles_a_delivery_whose_publisher_blows_up() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
            stub(occurrence, pending);
            stubServed(pending, publisher);
            doThrow(new IllegalStateException("Facebook delivery not found"))
                    .when(publisher).publish(occurrence);

            assertThatCode(() -> service.publishScheduledOccurrence(OCCURRENCE_ID)).doesNotThrowAnyException();

            assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(pending.getErrorMessage()).isEqualTo("Facebook delivery not found");
            verify(transactionManager).rollback(any());
            verify(publicationOccurrenceService).refreshStatus(occurrence);
        }

        /**
         * Un canal en échec ne doit priver aucun autre de sa diffusion, ni faire
         * échouer le balayage : l'échec est tracé sur la livraison, pas remonté.
         */
        @Test
        void serves_the_other_channels_even_when_one_fails() {
            PublicationOccurrence occurrence = occurrenceDue(5);
            PublicationDelivery facebook = delivery(occurrence, DeliveryStatus.PENDING);
            PublicationDelivery intramuros = delivery(occurrence, DeliveryStatus.PENDING);
            intramuros.setId(100L);
            intramuros.setChannel(DeliveryChannel.INTRAMUROS);
            stub(occurrence, facebook, intramuros);
            stubServed(facebook, publisher);
            when(publisherRegistry.forChannel(DeliveryChannel.INTRAMUROS)).thenReturn(Optional.empty());

            assertThatCode(() -> service.publishScheduledOccurrence(OCCURRENCE_ID)).doesNotThrowAnyException();

            verify(publisher).publish(occurrence);
            assertThat(intramuros.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        }

        /** Supprimée entre le balayage et sa prise en charge. */
        @Test
        void skips_an_occurrence_that_no_longer_exists() {
            when(publicationOccurrenceRepository.findById(OCCURRENCE_ID)).thenReturn(Optional.empty());

            assertThatCode(() -> service.publishScheduledOccurrence(OCCURRENCE_ID)).doesNotThrowAnyException();

            verify(publicationDeliveryRepository, never()).findAllByOccurrence(any());
            verifyNoInteractions(publisherRegistry, publicationOccurrenceService);
        }
    }
}
