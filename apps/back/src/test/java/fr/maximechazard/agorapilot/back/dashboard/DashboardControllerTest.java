package fr.maximechazard.agorapilot.back.dashboard;

import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP de {@code /api/dashboard}. */
@ApiControllerTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void returns_the_counters_and_the_upcoming_occurrences() throws Exception {
        PublicationOccurrenceDTO upcoming = new PublicationOccurrenceDTO(
                9L,
                LocalDateTime.of(2026, 10, 25, 18, 30),
                false,
                PublicationOccurrenceStatus.SCHEDULED,
                new PublicationDTO(3L, "Marché de Noël samedi", PublicationStatus.VERIFIED, List.of(), null),
                List.of(new PublicationDeliveryDTO(4L, 9L, DeliveryChannel.FACEBOOK, DeliveryStatus.PENDING, null, null, null)));
        when(dashboardService.getSummary()).thenReturn(new DashboardDTO(2, 6, 3, 1, List.of(upcoming)));

        mockMvc.perform(get("/api/dashboard"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.activeCampaigns").value(2))
               .andExpect(jsonPath("$.scheduledThisWeek").value(6))
               .andExpect(jsonPath("$.draftPublications").value(3))
               .andExpect(jsonPath("$.failedOccurrences").value(1))
               .andExpect(jsonPath("$.upcomingOccurrences[0].id").value(9))
               .andExpect(jsonPath("$.upcomingOccurrences[0].scheduledAt").value("2026-10-25T18:30:00"))
               .andExpect(jsonPath("$.upcomingOccurrences[0].publication.content").value("Marché de Noël samedi"))
               .andExpect(jsonPath("$.upcomingOccurrences[0].deliveries[0].channel").value("FACEBOOK"));
    }
}
