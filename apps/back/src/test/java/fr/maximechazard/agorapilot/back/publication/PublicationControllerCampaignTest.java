package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationCampaignDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de {@code PUT /api/publications/{id}/campaign}.
 */
@WebMvcTest(PublicationController.class)
@ActiveProfiles("dev")
class PublicationControllerCampaignTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private PublicationDeliveryService publicationDeliveryService;

    private static PublicationDTO publication(PublicationCampaignDTO campaign) {
        return new PublicationDTO(7L, "Marché de producteurs", PublicationStatus.DRAFT, List.of(), campaign);
    }

    @Test
    void renvoieLaPublicationRattachee() throws Exception {
        when(publicationService.setCampaign(7L, 3L))
                .thenReturn(publication(new PublicationCampaignDTO(3L, "Marché de Noël")));

        mockMvc.perform(put("/api/publications/7/campaign")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"campaignId\":3}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.campaign.id").value(3))
               .andExpect(jsonPath("$.campaign.name").value("Marché de Noël"));

        verify(publicationService).setCampaign(7L, 3L);
    }

    @Test
    void detacheAvecUnIdentifiantNul() throws Exception {
        when(publicationService.setCampaign(7L, null)).thenReturn(publication(null));

        mockMvc.perform(put("/api/publications/7/campaign")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"campaignId\":null}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.campaign").doesNotExist());

        verify(publicationService).setCampaign(7L, null);
    }

    /** Un corps vide vaut un détachement : la campagne absente, c'est aucune campagne. */
    @Test
    void detacheAvecUnCorpsVide() throws Exception {
        when(publicationService.setCampaign(7L, null)).thenReturn(publication(null));

        mockMvc.perform(put("/api/publications/7/campaign")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{}"))
               .andExpect(status().isOk());

        verify(publicationService).setCampaign(7L, null);
    }

    @Test
    void signaleUnePublicationInconnue() throws Exception {
        when(publicationService.setCampaign(404L, 3L))
                .thenThrow(new PublicationNotFoundException("Publication 404 introuvable"));

        mockMvc.perform(put("/api/publications/404/campaign")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"campaignId\":3}"))
               .andExpect(status().isNotFound());
    }

    @Test
    void signaleUneCampagneInconnue() throws Exception {
        when(publicationService.setCampaign(7L, 404L))
                .thenThrow(new CampaignNotFoundException("Campagne 404 introuvable"));

        mockMvc.perform(put("/api/publications/7/campaign")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"campaignId\":404}"))
               .andExpect(status().isNotFound());
    }
}
