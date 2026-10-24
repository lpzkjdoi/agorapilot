package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.requests.UpdatePublicationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP de {@code PUT} et {@code DELETE /api/publications/{id}}. */
@ApiControllerTest(PublicationController.class)
class PublicationControllerCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private PublicationDeliveryService publicationDeliveryService;

    @Test
    void updates_the_content_and_the_status() throws Exception {
        when(publicationService.update(eq(7L), any(UpdatePublicationRequest.class), anyBoolean()))
                .thenReturn(new PublicationDTO(7L, "Marché de producteurs, place du village", PublicationStatus.VERIFIED, List.of(), null,
                        LocalDateTime.of(2026, 10, 1, 9, 0), "camille", LocalDateTime.of(2026, 10, 24, 9, 0), "bruno"));

        mockMvc.perform(put("/api/publications/7")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"content\":\"Marché de producteurs, place du village\",\"status\":\"VERIFIED\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").value("Marché de producteurs, place du village"))
               .andExpect(jsonPath("$.status").value("VERIFIED"))
               .andExpect(jsonPath("$.createdBy").value("camille"))
               .andExpect(jsonPath("$.updatedBy").value("bruno"));

        verify(publicationService).update(eq(7L), argThat(request ->
                request.getContent().equals("Marché de producteurs, place du village")
                        && request.getStatus() == PublicationStatus.VERIFIED), anyBoolean());
    }

    @Test
    void refuses_a_blank_content() throws Exception {
        mockMvc.perform(put("/api/publications/7")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"content\":\"  \",\"status\":\"DRAFT\"}"))
               .andExpect(status().isBadRequest());

        verify(publicationService, never()).update(any(), any(), anyBoolean());
    }

    @Test
    void refuses_a_missing_status() throws Exception {
        mockMvc.perform(put("/api/publications/7")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"content\":\"Marché\"}"))
               .andExpect(status().isBadRequest());

        verify(publicationService, never()).update(any(), any(), anyBoolean());
    }

    @Test
    void answers_404_when_updating_an_unknown_publication() throws Exception {
        when(publicationService.update(eq(404L), any(), anyBoolean())).thenThrow(new PublicationNotFoundException("Publication 404 introuvable"));

        mockMvc.perform(put("/api/publications/404")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"content\":\"Marché\",\"status\":\"DRAFT\"}"))
               .andExpect(status().isNotFound());
    }

    @Test
    void deletes_with_no_content() throws Exception {
        mockMvc.perform(delete("/api/publications/7"))
               .andExpect(status().isNoContent());

        verify(publicationService).delete(7L);
    }

    @Test
    void answers_404_when_deleting_an_unknown_publication() throws Exception {
        doThrow(new PublicationNotFoundException("Publication 404 introuvable")).when(publicationService).delete(404L);

        mockMvc.perform(delete("/api/publications/404"))
               .andExpect(status().isNotFound());
    }

    @Test
    void answers_409_with_the_reason_when_a_delivery_is_imminent() throws Exception {
        doThrow(new OccurrenceNotModifiableException("La publication 7 a une diffusion en cours ou imminente"))
                .when(publicationService).delete(7L);

        mockMvc.perform(delete("/api/publications/7"))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("La publication 7 a une diffusion en cours ou imminente"));
    }
}
