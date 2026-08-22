package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couvre le contrat HTTP de {@code GET /api/occurrences/weekly} : un objet
 * indexé par jour, dont chaque occurrence porte des livraisons à plat.
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

    @Test
    void returns_the_week_indexed_by_day_with_flat_deliveries() throws Exception {
        PublicationOccurrenceDTO occurrence = new PublicationOccurrenceDTO(
                1L,
                LocalDateTime.of(2026, 8, 22, 18, 20, 5),
                new PublicationDTO(1L, "Test création publication", PublicationStatus.VERIFIED),
                List.of(new PublicationDeliveryDTO(
                        1L, 1L, DeliveryChannel.FACEBOOK, DeliveryStatus.PUBLISHED,
                        LocalDateTime.of(2026, 8, 22, 18, 20, 5), "1144547398751185_122106824271408514")));

        when(publicationOccurrenceService.getWeeklyOccurrences())
                .thenReturn(Map.of(DAY, List.of(occurrence), EMPTY_DAY, List.of()));

        mockMvc.perform(get("/api/occurrences/weekly"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$['" + DAY + "'][0].id").value(1))
               .andExpect(jsonPath("$['" + DAY + "'][0].publication.content").value("Test création publication"))
               .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].channel").value("FACEBOOK"))
               .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].status").value("PUBLISHED"))
               // Le garde-fou de la régression : aucune livraison ne doit
               // renvoyer vers son occurrence, sinon la réponse part en boucle.
               .andExpect(jsonPath("$['" + DAY + "'][0].deliveries[0].occurrence").doesNotExist())
               .andExpect(jsonPath("$['" + EMPTY_DAY + "']").isEmpty());
    }
}
