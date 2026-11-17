package fr.maximechazard.agorapilot.back.publication.export;

import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotVerifiedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP de {@code GET /api/publications/{id}/xlsx}. */
@ApiControllerTest(EventProxyExportController.class)
class EventProxyExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventProxyExportService exportService;

    @Test
    void downloads_the_workbook_as_an_attachment() throws Exception {
        byte[] workbook = {1, 2, 3};
        when(exportService.export(7L)).thenReturn(new EventProxyExport("EventProxy-2026-11-17-publication-7.xlsx", workbook));

        mockMvc.perform(get("/api/publications/7/xlsx"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(EventProxyExportController.XLSX))
               .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                       org.hamcrest.Matchers.containsString("attachment; filename=\"EventProxy-2026-11-17-publication-7.xlsx\"")))
               .andExpect(content().bytes(workbook));
    }

    @Test
    void returns_409_for_a_draft() throws Exception {
        when(exportService.export(7L)).thenThrow(new PublicationNotVerifiedException(7L));

        mockMvc.perform(get("/api/publications/7/xlsx")).andExpect(status().isConflict());
    }
}
