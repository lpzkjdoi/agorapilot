package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.dtos.MediaSummaryDTO;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.DuplicateMediaException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de {@code PUT /api/publications/{id}/medias}.
 */
@WebMvcTest(PublicationController.class)
@ActiveProfiles("dev")
class PublicationControllerMediasTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private PublicationDeliveryService publicationDeliveryService;

    @Test
    void returns_the_publication_with_its_medias_in_the_order_that_was_sent() throws Exception {
        when(publicationService.setMedias(eq(7L), any())).thenReturn(publication(
                new MediaSummaryDTO(3L, "Affiche", "a.png", "image/png", 1200, 1800, null),
                new MediaSummaryDTO(1L, null, "b.jpg", "image/jpeg", 800, 600, "Une photo")));

        mockMvc.perform(put("/api/publications/7/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"mediaIds\":[3,1]}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.medias.length()").value(2))
               .andExpect(jsonPath("$.medias[0].id").value(3))
               .andExpect(jsonPath("$.medias[1].id").value(1))
               .andExpect(jsonPath("$.medias[1].altText").value("Une photo"));

        verify(publicationService).setMedias(7L, List.of(3L, 1L));
    }

    @Test
    void accepts_an_empty_list_to_detach_everything() throws Exception {
        when(publicationService.setMedias(eq(7L), any())).thenReturn(publication());

        mockMvc.perform(put("/api/publications/7/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"mediaIds\":[]}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.medias.length()").value(0));

        verify(publicationService).setMedias(7L, List.of());
    }

    @Test
    void returns_400_when_the_list_is_missing() throws Exception {
        mockMvc.perform(put("/api/publications/7/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void returns_400_when_the_same_media_appears_twice() throws Exception {
        when(publicationService.setMedias(any(), any()))
                .thenThrow(new DuplicateMediaException("Le média 3 est présent deux fois dans la liste"));

        mockMvc.perform(put("/api/publications/7/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"mediaIds\":[3,3]}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void returns_404_for_an_unknown_publication() throws Exception {
        when(publicationService.setMedias(any(), any()))
                .thenThrow(new PublicationNotFoundException("Publication 404 introuvable"));

        mockMvc.perform(put("/api/publications/404/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"mediaIds\":[3]}"))
               .andExpect(status().isNotFound());
    }

    @Test
    void returns_404_for_an_unknown_media() throws Exception {
        when(publicationService.setMedias(any(), any()))
                .thenThrow(new MediaNotFoundException("Média 404 introuvable"));

        mockMvc.perform(put("/api/publications/7/medias")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"mediaIds\":[404]}"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("Média 404 introuvable"));
    }

    private PublicationDTO publication(MediaSummaryDTO... medias) {
        return new PublicationDTO(7L, "Marché de producteurs", PublicationStatus.DRAFT, List.of(medias));
    }
}
