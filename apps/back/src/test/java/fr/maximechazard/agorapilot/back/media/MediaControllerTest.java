package fr.maximechazard.agorapilot.back.media;

import fr.maximechazard.agorapilot.back.media.dtos.MediaContent;
import fr.maximechazard.agorapilot.back.media.dtos.MediaDTO;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.media.exceptions.UnsupportedMediaFileTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Couvre le contrat HTTP de {@code /api/medias} — en particulier les codes
 * d'erreur et les en-têtes de cache sur lesquels le front s'appuie.
 */
@WebMvcTest(MediaController.class)
@ActiveProfiles("dev")
class MediaControllerTest {

    private static final byte[] CONTENT = "une affiche".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @Test
    void returns_201_and_the_media_after_an_upload() throws Exception {
        when(mediaService.upload(any(), any(), any())).thenReturn(mediaDTO());

        mockMvc.perform(multipart("/api/medias")
                       .file(new MockMultipartFile("file", "affiche.png", "image/png", CONTENT))
                       .param("title", "Fête de la musique"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(5))
               .andExpect(jsonPath("$.contentType").value("image/png"))
               .andExpect(jsonPath("$.width").value(1200));
    }

    @Test
    void returns_415_when_the_content_is_not_an_accepted_format() throws Exception {
        when(mediaService.upload(any(), any(), any()))
                .thenThrow(new UnsupportedMediaFileTypeException("Format non reconnu."));

        mockMvc.perform(multipart("/api/medias")
                       .file(new MockMultipartFile("file", "script.jpg", "image/jpeg", CONTENT)))
               .andExpect(status().isUnsupportedMediaType())
               .andExpect(jsonPath("$.code").value(415))
               .andExpect(jsonPath("$.message").value("Format non reconnu."));
    }

    @Test
    void returns_413_with_a_readable_body_when_the_file_is_too_large() throws Exception {
        when(mediaService.upload(any(), any(), any()))
                .thenThrow(new MaxUploadSizeExceededException(15_728_640L));

        mockMvc.perform(multipart("/api/medias")
                       .file(new MockMultipartFile("file", "enorme.png", "image/png", CONTENT)))
               .andExpect(status().isPayloadTooLarge())
               .andExpect(jsonPath("$.code").value(413));
    }

    @Test
    void serves_the_binary_with_its_real_type_and_an_immutable_cache() throws Exception {
        when(mediaService.loadContent(5L)).thenReturn(new MediaContent(mediaDTO(), new ByteArrayResource(CONTENT)));

        mockMvc.perform(get("/api/medias/5/file"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.IMAGE_PNG))
               .andExpect(content().bytes(CONTENT))
               .andExpect(header().string(HttpHeaders.ETAG, "\"cafe\""))
               .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("immutable")))
               .andExpect(header().string("X-Content-Type-Options", "nosniff"))
               .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                       org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    void returns_404_for_the_binary_of_an_unknown_media() throws Exception {
        when(mediaService.loadContent(any())).thenThrow(new MediaNotFoundException("Média 404 introuvable"));

        mockMvc.perform(get("/api/medias/404/file"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void passes_the_archived_filter_through_to_the_service() throws Exception {
        when(mediaService.getAll(false)).thenReturn(List.of(mediaDTO()));

        mockMvc.perform(get("/api/medias").param("archived", "false"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updates_the_metadata_of_a_media() throws Exception {
        when(mediaService.update(any(), any())).thenReturn(mediaDTO());

        mockMvc.perform(patch("/api/medias/5")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"title\":\"Nouveau titre\"}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void returns_204_after_a_deletion() throws Exception {
        mockMvc.perform(delete("/api/medias/5")).andExpect(status().isNoContent());
    }

    @Test
    void returns_404_when_deleting_a_media_that_is_not_there() throws Exception {
        doThrow(new MediaNotFoundException("Média 404 introuvable")).when(mediaService).delete(404L);

        mockMvc.perform(delete("/api/medias/404")).andExpect(status().isNotFound());
    }

    private MediaDTO mediaDTO() {
        return new MediaDTO(
                5L, "Fête de la musique", "affiche.png", "image/png", (long) CONTENT.length,
                1200, 1800, "Affiche de la fête", "cafe", false,
                LocalDateTime.of(2026, 8, 23, 10, 0), LocalDateTime.of(2026, 8, 23, 10, 0));
    }
}
