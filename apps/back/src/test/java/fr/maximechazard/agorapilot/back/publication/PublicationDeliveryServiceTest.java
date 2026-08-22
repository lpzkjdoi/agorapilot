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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationDeliveryServiceTest {

    private static final long PUBLICATION_ID = 7L;

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

    private PublicationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new PublicationDeliveryService(
                publicationRepository,
                publicationOccurrenceRepository,
                publicationDeliveryRepository,
                publisherRegistry,
                new PublicationDeliveryMapper()
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
        occurrence.setId(42L);
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
        when(publicationDeliveryRepository.findAllByOccurrence(any())).thenReturn(List.of());

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
        when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED)));

        PublicationDeliveryDTO dto = service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

        verify(publisher).publish(occurrence);
        assertThat(dto.status()).isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(dto.channel()).isEqualTo(DeliveryChannel.FACEBOOK);
        assertThat(dto.occurrenceId()).isEqualTo(occurrence.getId());
        assertThat(dto.externalId()).isEqualTo("123_456");
    }

    @Test
    void marks_the_occurrence_published_once_every_channel_is_served() {
        PublicationOccurrence occurrence = stubSuccessfulPublication(DeliveryStatus.PUBLISHED);
        when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED)));

        service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

        assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.PUBLISHED);
        verify(publicationOccurrenceRepository).save(occurrence);
    }

    @Test
    void leaves_the_occurrence_scheduled_while_a_channel_is_still_pending() {
        PublicationOccurrence occurrence = stubSuccessfulPublication(DeliveryStatus.PUBLISHED);

        PublicationDelivery pending = delivery(occurrence, DeliveryStatus.PENDING);
        pending.setChannel(DeliveryChannel.INTRAMUROS);
        when(publicationDeliveryRepository.findAllByOccurrence(occurrence))
                .thenReturn(List.of(delivery(occurrence, DeliveryStatus.PUBLISHED), pending));

        service.publishNow(PUBLICATION_ID, DeliveryChannel.FACEBOOK);

        assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
        verify(publicationOccurrenceRepository, never()).save(occurrence);
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

        assertThat(occurrence.getStatus()).isEqualTo(PublicationOccurrenceStatus.SCHEDULED);
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
