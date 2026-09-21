package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                status,
                new PublicationDTO(1L, "Test création publication", PublicationStatus.VERIFIED, List.of()),
                List.of(new PublicationDeliveryDTO(
                        1L, 1L, DeliveryChannel.FACEBOOK, deliveryStatus,
                        LocalDateTime.of(2026, 8, 22, 18, 20, 5), "1144547398751185_122106824271408514")));
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
    }
}
