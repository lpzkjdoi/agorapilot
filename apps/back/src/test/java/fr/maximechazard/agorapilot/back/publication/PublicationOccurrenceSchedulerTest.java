package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L'ordonnanceur est la pièce qui rend la planification réelle : ces tests
 * verrouillent ce qu'il retient (les occurrences en retard, et elles seules) et
 * le fait qu'une diffusion en échec n'emporte pas le reste du lot.
 */
@ExtendWith(MockitoExtension.class)
class PublicationOccurrenceSchedulerTest {

    @Mock
    private PublicationOccurrenceRepository publicationOccurrenceRepository;
    @Mock
    private PublicationDeliveryService publicationDeliveryService;

    @InjectMocks
    private PublicationOccurrenceScheduler scheduler;

    private static PublicationOccurrence occurrence(long id) {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setId(id);
        occurrence.setScheduledAt(LocalDateTime.now().minusMinutes(5));
        return occurrence;
    }

    @Test
    void serves_only_the_scheduled_occurrences_whose_time_has_passed() {
        when(publicationOccurrenceRepository.findAllByScheduledAtBeforeAndStatus(any(), any()))
                .thenReturn(List.of(occurrence(1L)));

        LocalDateTime before = LocalDateTime.now();
        scheduler.publishDueOccurrences();

        ArgumentCaptor<LocalDateTime> instant = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(publicationOccurrenceRepository).findAllByScheduledAtBeforeAndStatus(
                instant.capture(), eq(PublicationOccurrenceStatus.SCHEDULED));
        assertThat(instant.getValue()).isAfterOrEqualTo(before);

        verify(publicationDeliveryService).publishScheduledOccurrence(1L);
    }

    @Test
    void does_nothing_when_no_occurrence_is_due() {
        when(publicationOccurrenceRepository.findAllByScheduledAtBeforeAndStatus(any(), any()))
                .thenReturn(List.of());

        scheduler.publishDueOccurrences();

        verifyNoInteractions(publicationDeliveryService);
        assertThat(scheduler.getLastError()).isNull();
    }

    /**
     * Les occurrences suivantes sont déjà en retard : elles doivent partir
     * malgré l'échec de la précédente.
     */
    @Test
    void keeps_serving_the_batch_when_one_occurrence_blows_up() {
        when(publicationOccurrenceRepository.findAllByScheduledAtBeforeAndStatus(any(), any()))
                .thenReturn(List.of(occurrence(1L), occurrence(2L)));
        doThrow(new RuntimeException("base injoignable"))
                .when(publicationDeliveryService).publishScheduledOccurrence(1L);

        assertThatCode(() -> scheduler.publishDueOccurrences()).doesNotThrowAnyException();

        verify(publicationDeliveryService).publishScheduledOccurrence(2L);
        assertThat(scheduler.getLastError()).isEqualTo("Occurrence 1: base injoignable");
        assertThat(scheduler.getLastErrorAt()).isNotNull();
    }

    /**
     * Effacée au balayage suivant, l'erreur ne vivrait qu'une minute : personne
     * ne la lirait jamais. Elle reste lisible, datée, jusqu'à la suivante.
     */
    @Test
    void keeps_the_last_error_after_a_clean_run() {
        when(publicationOccurrenceRepository.findAllByScheduledAtBeforeAndStatus(any(), any()))
                .thenReturn(List.of(occurrence(1L)));
        doThrow(new RuntimeException("base injoignable"))
                .when(publicationDeliveryService).publishScheduledOccurrence(anyLong());

        scheduler.publishDueOccurrences();
        LocalDateTime errorAt = scheduler.getLastErrorAt();

        when(publicationOccurrenceRepository.findAllByScheduledAtBeforeAndStatus(any(), any()))
                .thenReturn(List.of());
        scheduler.publishDueOccurrences();

        assertThat(scheduler.getLastError()).isEqualTo("Occurrence 1: base injoignable");
        assertThat(scheduler.getLastErrorAt()).isEqualTo(errorAt);
        assertThat(scheduler.getLastRunAt()).isAfterOrEqualTo(errorAt);
    }
}
