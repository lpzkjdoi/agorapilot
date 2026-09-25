package fr.maximechazard.agorapilot.back.publication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchedulerAdminController.class)
@ActiveProfiles("dev")
class SchedulerAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationOccurrenceScheduler scheduler;

    @MockitoBean
    private SchedulingProperties schedulingProperties;

    @Test
    void exposes_the_last_run_the_last_error_and_the_settings() throws Exception {
        when(scheduler.getLastRunAt()).thenReturn(LocalDateTime.of(2026, 9, 25, 10, 1));
        when(scheduler.getLastErrorAt()).thenReturn(LocalDateTime.of(2026, 9, 25, 9, 12));
        when(scheduler.getLastError()).thenReturn("Occurrence 4: base injoignable");
        when(schedulingProperties.occurrencesDelay()).thenReturn(Duration.ofMinutes(1));
        when(schedulingProperties.maxLateness()).thenReturn(Duration.ofHours(1));

        mockMvc.perform(get("/admin/scheduler/status"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.lastRunAt").value("2026-09-25T10:01:00"))
               .andExpect(jsonPath("$.lastErrorAt").value("2026-09-25T09:12:00"))
               .andExpect(jsonPath("$.lastError").value("Occurrence 4: base injoignable"))
               .andExpect(jsonPath("$.occurrencesDelay").value("PT1M"))
               .andExpect(jsonPath("$.maxLateness").value("PT1H"));
    }

    /** Avant le premier balayage : rien n'a tourné, rien n'a échoué. */
    @Test
    void reports_nulls_before_the_first_run() throws Exception {
        when(schedulingProperties.occurrencesDelay()).thenReturn(Duration.ofMinutes(1));
        when(schedulingProperties.maxLateness()).thenReturn(Duration.ofHours(1));

        mockMvc.perform(get("/admin/scheduler/status"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.lastRunAt").doesNotExist())
               .andExpect(jsonPath("$.lastError").doesNotExist());
    }
}
