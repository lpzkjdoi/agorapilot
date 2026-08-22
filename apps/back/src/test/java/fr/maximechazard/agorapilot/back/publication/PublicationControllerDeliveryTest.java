package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.DeliveryFailedException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.UnsupportedDeliveryChannelException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couvre le contrat HTTP de {@code POST /api/publications/{id}/deliveries},
 * en particulier les codes d'erreur sur lesquels le front s'appuie.
 */
@WebMvcTest(PublicationController.class)
@ActiveProfiles("dev")
class PublicationControllerDeliveryTest {

    private static final String BODY = "{\"channel\":\"FACEBOOK\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @MockitoBean
    private PublicationDeliveryService publicationDeliveryService;

    @Test
    void returns_201_and_the_delivery_when_the_post_goes_through() throws Exception {
        when(publicationDeliveryService.publishNow(7L, DeliveryChannel.FACEBOOK))
                .thenReturn(new PublicationDeliveryDTO(
                        99L, 42L, DeliveryChannel.FACEBOOK, DeliveryStatus.PUBLISHED,
                        LocalDateTime.of(2026, 8, 22, 10, 0), "123_456"));

        mockMvc.perform(post("/api/publications/7/deliveries")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(BODY))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.status").value("PUBLISHED"))
               .andExpect(jsonPath("$.externalId").value("123_456"))
               .andExpect(jsonPath("$.occurrenceId").value(42));
    }

    @Test
    void returns_404_for_an_unknown_publication() throws Exception {
        when(publicationDeliveryService.publishNow(any(), any()))
                .thenThrow(new PublicationNotFoundException("Publication with id 7 does not exist."));

        mockMvc.perform(post("/api/publications/7/deliveries")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(BODY))
               .andExpect(status().isNotFound());
    }

    @Test
    void returns_502_with_the_reason_when_the_channel_refuses_the_post() throws Exception {
        when(publicationDeliveryService.publishNow(any(), any()))
                .thenThrow(new DeliveryFailedException("No token available"));

        mockMvc.perform(post("/api/publications/7/deliveries")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(BODY))
               .andExpect(status().isBadGateway())
               .andExpect(jsonPath("$.message").value("No token available"));
    }

    @Test
    void returns_501_for_a_channel_without_publisher() throws Exception {
        when(publicationDeliveryService.publishNow(any(), any()))
                .thenThrow(new UnsupportedDeliveryChannelException("No publisher available for channel INTRAMUROS."));

        mockMvc.perform(post("/api/publications/7/deliveries")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"channel\":\"INTRAMUROS\"}"))
               .andExpect(status().isNotImplemented());
    }

    @Test
    void rejects_a_request_without_channel() throws Exception {
        mockMvc.perform(post("/api/publications/7/deliveries")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{}"))
               .andExpect(status().isBadRequest());
    }
}
