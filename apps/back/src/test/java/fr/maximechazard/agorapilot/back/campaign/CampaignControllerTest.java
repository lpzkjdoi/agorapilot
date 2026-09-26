package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotClosableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationCampaignDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couvre le contrat HTTP de {@code /api/campaigns}.
 */
@WebMvcTest(CampaignController.class)
@ActiveProfiles("dev")
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService campaignService;

    private static CampaignDTO campaign() {
        return new CampaignDTO(
                3L,
                "Marché de Noël",
                "Toutes les annonces du marché",
                LocalDateTime.of(2026, 12, 1, 9, 0),
                LocalDateTime.of(2026, 12, 24, 23, 59),
                CampaignStatus.SCHEDULED,
                List.of(new PublicationDTO(
                        8L,
                        "Rendez-vous samedi",
                        PublicationStatus.VERIFIED,
                        List.of(),
                        new PublicationCampaignDTO(3L, "Marché de Noël")
                ))
        );
    }

    @Nested
    class GetAll {

        @Test
        void renvoieLesCampagnesAvecLeursPublications() throws Exception {
            when(campaignService.getAll()).thenReturn(List.of(campaign()));

            mockMvc.perform(get("/api/campaigns"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$[0].id").value(3))
                   .andExpect(jsonPath("$[0].name").value("Marché de Noël"))
                   .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                   .andExpect(jsonPath("$[0].startDate").value("2026-12-01T09:00:00"))
                   .andExpect(jsonPath("$[0].publications[0].id").value(8))
                   .andExpect(jsonPath("$[0].publications[0].content").value("Rendez-vous samedi"));
        }

        /**
         * L'endpoint sérialisait l'entité {@link Campaign}, dont les publications
         * référencent en retour leur campagne : le corps ne se parsait pas. Le
         * retour existe toujours, mais sous une forme réduite qui ne reboucle
         * pas — la campagne d'une publication ne porte pas ses publications.
         */
        @Test
        void neRebouclePasDeLaPublicationVersSesPublications() throws Exception {
            when(campaignService.getAll()).thenReturn(List.of(campaign()));

            mockMvc.perform(get("/api/campaigns"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$[0].publications[0].campaign.name").value("Marché de Noël"))
                   .andExpect(jsonPath("$[0].publications[0].campaign.publications").doesNotExist())
                   .andExpect(jsonPath("$[0].archived").doesNotExist())
                   .andExpect(jsonPath("$[0].createdAt").doesNotExist());
        }

        @Test
        void renvoieUnTableauVideSansCampagne() throws Exception {
            when(campaignService.getAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/campaigns"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class Create {

        @Test
        void creeLaCampagne() throws Exception {
            when(campaignService.create(any(CreateCampaignRequest.class))).thenReturn(campaign());

            mockMvc.perform(post("/api/campaigns")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"name":"Marché de Noël","description":"Toutes les annonces du marché",
                                    "startDate":"2026-12-01T09:00:00","endDate":"2026-12-24T23:59:00"}"""))
                   .andExpect(status().isCreated())
                   .andExpect(jsonPath("$.id").value(3));
        }

        @Test
        void refuseUneCampagneSansNom() throws Exception {
            mockMvc.perform(post("/api/campaigns")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"name":"  ","description":"Sans nom"}"""))
                   .andExpect(status().isBadRequest());

            verify(campaignService, never()).create(any());
        }

        @Test
        void refuseUneDateDeDebutPasseeALaCreation() throws Exception {
            mockMvc.perform(post("/api/campaigns")
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("""
                                   {"name":"Rétroactive","startDate":"2020-01-01T09:00:00"}"""))
                   .andExpect(status().isBadRequest());

            verify(campaignService, never()).create(any());
        }
    }

    @Nested
    class Close {

        @Test
        void clotureLaCampagne() throws Exception {
            when(campaignService.close(3L)).thenReturn(campaign());

            mockMvc.perform(post("/api/campaigns/3/closure"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.id").value(3));
        }

        @Test
        void signaleUneCampagneInconnue() throws Exception {
            when(campaignService.close(404L))
                    .thenThrow(new CampaignNotFoundException("Campagne 404 introuvable"));

            mockMvc.perform(post("/api/campaigns/404/closure"))
                   .andExpect(status().isNotFound());
        }

        @Test
        void refuseUneCampagneQueSonEtatNePermetPasDeCloturer() throws Exception {
            when(campaignService.close(3L))
                    .thenThrow(new CampaignNotClosableException("La campagne 3 n'a pas encore commencé"));

            mockMvc.perform(post("/api/campaigns/3/closure"))
                   .andExpect(status().isConflict());
        }
    }
}
