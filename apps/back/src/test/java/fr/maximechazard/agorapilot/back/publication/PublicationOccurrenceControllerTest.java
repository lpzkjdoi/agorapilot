package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couvre le contrat HTTP de {@code /api/occurrences} : le calendrier de la
 * semaine, et la planification d'une diffusion.
 */
@WebMvcTest(PublicationOccurrenceController.class)
@ActiveProfiles("dev")
class PublicationOccurrenceControllerTest {

    private static final String DAY = "2026-08-22T00:00";
    private static final String EMPTY_DAY = "2026-08-23T00:00";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationOccurrenceService publicationOccurrenceService;

    private static PublicationOccurrenceDTO occurrence(PublicationOccurrenceStatus status, DeliveryStatus deliveryStatus) {
        return new PublicationOccurrenceDTO(
                1L,
                LocalDateTime.of(2026, 8, 22, 18, 20, 5),
                false,
                status,
                new PublicationDTO(1L, "Test création publication", PublicationStatus.VERIFIED, List.of(), null),
                List.of(new PublicationDeliveryDTO(
                        1L, 1L, DeliveryChannel.FACEBOOK, deliveryStatus,
                        LocalDateTime.of(2026, 8, 22, 18, 20, 5), "1144547398751185_122106824271408514", null)));
    }

    @Nested
    class Weekly {

        @Test
        void returns_the_week_indexed_by_day_with_flat_deliveries() throws Exception {
            when(publicationOccurrenceService.getWeeklyOccurrences())
                    .thenReturn(Map.of(
                            DAY, List.of(occurrence(PublicationOccurrenceStatus.PUBLISHED, DeliveryStatus.PUBLISHED)),
                            EMPTY_DAY, List.of()));

            mockMvc.perform(get("/api/occurrences/weekly"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$['" + DAY + "'][0].id").value(1))
                   .andExpect(jsonPath("$['" + DAY + "'][0].status").value("PUBLISHED"))
                   .andExpect(jsonPath("$['" + DAY + "'][0].publication.content").value("Test création publication"))
                   .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].channel").value("FACEBOOK"))
                   .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].status").value("PUBLISHED"))
                   // Le garde-fou de la régression : aucune livraison ne doit
                   // renvoyer vers son occurrence, sinon la réponse part en boucle.
                   .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].occurrence").doesNotExist())
                   .andExpect(jsonPath("$['" + EMPTY_DAY + "']").isEmpty());
        }
    }

    @Nested
    class Create {

        private static final String BODY = """
                {"publicationId":1,"scheduledAt":"2099-08-22T18:20:05","channels":["FACEBOOK"]}""";

        @Test
        void returns_201_and_the_scheduled_occurrence() throws Exception {
            when(publicationOccurrenceService.create(any()))
                    .thenReturn(occurrence(PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING));

            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content(BODY))
                   .andExpect(status().isCreated())
                   .andExpect(jsonPath("$.id").value(1))
                   .andExpect(jsonPath("$.status").value("SCHEDULED"))
                   .andExpect(jsonPath("$.deliveries[0].status").value("PENDING"));
        }

        @Test
        void returns_404_for_an_unknown_publication() throws Exception {
            when(publicationOccurrenceService.create(any()))
                    .thenThrow(new PublicationNotFoundException("Publication with id 1 does not exist."));

            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content(BODY))
                   .andExpect(status().isNotFound());
        }

        @Test
        void returns_501_for_a_channel_without_publisher() throws Exception {
            when(publicationOccurrenceService.create(any()))
                    .thenThrow(new UnsupportedDeliveryChannelException("No publisher available for channel INTRAMUROS."));

            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"scheduledAt":"2099-08-22T18:20:05","channels":["INTRAMUROS"]}"""))
                   .andExpect(status().isNotImplemented());
        }

        /** Une date passée serait diffusée au balayage suivant, sans l'avoir demandé. */
        @Test
        void rejects_a_date_in_the_past() throws Exception {
            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"scheduledAt":"2020-08-22T18:20:05","channels":["FACEBOOK"]}"""))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).create(any());
        }

        @Test
        void rejects_a_request_without_channel() throws Exception {
            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"scheduledAt":"2099-08-22T18:20:05","channels":[]}"""))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).create(any());
        }

        /** Le cas courant : un jour, l'heure est choisie par le back. */
        @Test
        void accepts_a_day_without_time() throws Exception {
            when(publicationOccurrenceService.create(any()))
                    .thenReturn(occurrence(PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING));

            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"date":"2099-10-03","channels":["FACEBOOK"]}"""))
                   .andExpect(status().isCreated())
                   .andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        void rejects_both_a_day_and_a_time() throws Exception {
            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"date":"2099-10-03","scheduledAt":"2099-10-03T18:00:00","channels":["FACEBOOK"]}"""))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).create(any());
        }

        @Test
        void rejects_neither_a_day_nor_a_time() throws Exception {
            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"channels":["FACEBOOK"]}"""))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).create(any());
        }

        @Test
        void returns_400_with_the_reason_when_the_day_cannot_take_it() throws Exception {
            when(publicationOccurrenceService.create(any()))
                    .thenThrow(new InvalidScheduleException("la fenêtre 15:00–22:00 est passée"));

            mockMvc.perform(post("/api/occurrences")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"publicationId":1,"date":"2099-10-03","channels":["FACEBOOK"]}"""))
                   .andExpect(status().isBadRequest())
                   .andExpect(jsonPath("$.message").value("la fenêtre 15:00–22:00 est passée"));
        }
    }

    @Nested
    class Range {

        @Test
        void lists_the_occurrences_between_two_days() throws Exception {
            when(publicationOccurrenceService.findBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                    .thenReturn(List.of(occurrence(PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING)));

            mockMvc.perform(get("/api/occurrences").param("from", "2026-10-01").param("to", "2026-11-01"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$[0].id").value(1));
        }

        @Test
        void requires_both_bounds() throws Exception {
            mockMvc.perform(get("/api/occurrences").param("from", "2026-10-01"))
                   .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Reschedule {

        @Test
        void returns_the_rescheduled_occurrence() throws Exception {
            when(publicationOccurrenceService.reschedule(eq(1L), any()))
                    .thenReturn(occurrence(PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING));

            mockMvc.perform(put("/api/occurrences/1/schedule")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04","time":"18:30"}"""))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void requires_a_day() throws Exception {
            mockMvc.perform(put("/api/occurrences/1/schedule")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"time":"18:30"}"""))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).reschedule(any(), any());
        }

        @Test
        void returns_404_for_an_unknown_occurrence() throws Exception {
            when(publicationOccurrenceService.reschedule(eq(9L), any()))
                    .thenThrow(new OccurrenceNotFoundException("Occurrence with id 9 does not exist."));

            mockMvc.perform(put("/api/occurrences/9/schedule")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04"}"""))
                   .andExpect(status().isNotFound());
        }

        @Test
        void returns_409_for_an_occurrence_already_served_or_imminent() throws Exception {
            when(publicationOccurrenceService.reschedule(eq(1L), any()))
                    .thenThrow(new OccurrenceNotModifiableException("imminente"));

            mockMvc.perform(put("/api/occurrences/1/schedule")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04"}"""))
                   .andExpect(status().isConflict())
                   .andExpect(jsonPath("$.message").value("imminente"));
        }
    }

    @Nested
    class Retry {

        @Test
        void returns_the_occurrence_back_in_the_queue() throws Exception {
            when(publicationOccurrenceService.retry(eq(1L), any()))
                    .thenReturn(occurrence(PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING));

            mockMvc.perform(post("/api/occurrences/1/retry")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04"}"""))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.status").value("SCHEDULED"))
                   .andExpect(jsonPath("$.deliveries[0].status").value("PENDING"));
        }

        @Test
        void requires_a_day() throws Exception {
            mockMvc.perform(post("/api/occurrences/1/retry")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("{}"))
                   .andExpect(status().isBadRequest());

            verify(publicationOccurrenceService, never()).retry(any(), any());
        }

        @Test
        void returns_404_for_an_unknown_occurrence() throws Exception {
            when(publicationOccurrenceService.retry(eq(9L), any()))
                    .thenThrow(new OccurrenceNotFoundException("Occurrence with id 9 does not exist."));

            mockMvc.perform(post("/api/occurrences/9/retry")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04"}"""))
                   .andExpect(status().isNotFound());
        }

        @Test
        void returns_409_for_an_occurrence_that_did_not_fail() throws Exception {
            when(publicationOccurrenceService.retry(eq(1L), any()))
                    .thenThrow(new OccurrenceNotModifiableException("pas en échec"));

            mockMvc.perform(post("/api/occurrences/1/retry")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2099-10-04"}"""))
                   .andExpect(status().isConflict())
                   .andExpect(jsonPath("$.message").value("pas en échec"));
        }

        @Test
        void returns_400_with_the_reason_when_the_day_cannot_take_it() throws Exception {
            when(publicationOccurrenceService.retry(eq(1L), any()))
                    .thenThrow(new InvalidScheduleException("fenêtre passée"));

            mockMvc.perform(post("/api/occurrences/1/retry")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"date":"2026-09-26"}"""))
                   .andExpect(status().isBadRequest())
                   .andExpect(jsonPath("$.message").value("fenêtre passée"));
        }
    }

    @Nested
    class Cancel {

        @Test
        void returns_204() throws Exception {
            mockMvc.perform(delete("/api/occurrences/1"))
                   .andExpect(status().isNoContent());

            verify(publicationOccurrenceService).cancel(1L);
        }

        @Test
        void returns_409_when_it_can_no_longer_be_cancelled() throws Exception {
            doThrow(new OccurrenceNotModifiableException("déjà publiée"))
                    .when(publicationOccurrenceService).cancel(1L);

            mockMvc.perform(delete("/api/occurrences/1"))
                   .andExpect(status().isConflict());
        }
    }
}
