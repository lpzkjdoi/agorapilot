package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import fr.maximechazard.agorapilot.back.publication.recurrence.dtos.OccurrenceSeriesDTO;
import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP de {@code /api/occurrence-series}. */
@ApiControllerTest(OccurrenceSeriesController.class)
class OccurrenceSeriesControllerTest {

    private static final OccurrenceSeriesDTO SERIES = new OccurrenceSeriesDTO(
            3L, 7L, "FREQ=WEEKLY;INTERVAL=2", LocalDate.of(2026, 11, 23), LocalTime.of(18, 0),
            Set.of(DeliveryChannel.FACEBOOK), OccurrenceSeriesStatus.ACTIVE, null, 4, "maxime", null);

    private static final String BODY = """
            {"publicationId":7,"channels":["FACEBOOK"],"rule":"FREQ=WEEKLY;INTERVAL=2","startDate":"2026-11-23","time":"18:00"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OccurrenceSeriesService seriesService;

    @Test
    void creates_a_series() throws Exception {
        when(seriesService.create(any())).thenReturn(SERIES);

        mockMvc.perform(post("/api/occurrence-series").contentType(MediaType.APPLICATION_JSON).content(BODY))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.rule").value("FREQ=WEEKLY;INTERVAL=2"))
               .andExpect(jsonPath("$.publishTime").value("18:00:00"))
               .andExpect(jsonPath("$.upcoming").value(4));
    }

    @Test
    void returns_400_with_the_reason_for_an_invalid_rule() throws Exception {
        when(seriesService.create(any())).thenThrow(new InvalidScheduleException("Règle de répétition invalide : FREQ manquant."));

        mockMvc.perform(post("/api/occurrence-series").contentType(MediaType.APPLICATION_JSON).content(BODY))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Règle de répétition invalide : FREQ manquant."));
    }

    @Test
    void returns_400_without_a_rule_or_a_channel() throws Exception {
        mockMvc.perform(post("/api/occurrence-series").contentType(MediaType.APPLICATION_JSON)
                       .content("{\"publicationId\":7,\"channels\":[],\"startDate\":\"2026-11-23\"}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void lists_the_series_of_a_publication_and_stops_one() throws Exception {
        when(seriesService.findByPublication(7L)).thenReturn(List.of(SERIES));
        when(seriesService.stop(3L)).thenReturn(SERIES);

        mockMvc.perform(get("/api/occurrence-series").param("publicationId", "7"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(3));
        mockMvc.perform(delete("/api/occurrence-series/3")).andExpect(status().isOk());
    }
}
