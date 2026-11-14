package fr.maximechazard.agorapilot.back.notification;

import fr.maximechazard.agorapilot.back.notification.summary.DailySummaryService;
import fr.maximechazard.agorapilot.back.support.ApiControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.discord;
import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiControllerTest(NotificationAdminController.class)
class NotificationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private DailySummaryService dailySummaryService;
    @MockitoBean
    private NotificationProperties properties;
    /** Hors du slice MVC : l'horloge d'AppConfig n'est pas chargée. */
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NotificationFixtures.NOW);
    }

    /** Les cibles sont listées sans leur URL : elle porte le jeton du webhook. */
    @Test
    void lists_the_targets_without_their_url() throws Exception {
        when(notificationService.targets()).thenReturn(List.of(
                discord("alertes", "https://discord.test/api/webhooks/2/secret", NotificationType.DELIVERY_FAILED),
                discord("programme-du-jour", "", NotificationType.DAILY_SUMMARY)));

        mockMvc.perform(get("/admin/notifications/targets"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].name").value("alertes"))
               .andExpect(jsonPath("$[0].type").value("DISCORD"))
               .andExpect(jsonPath("$[0].events[0]").value("DELIVERY_FAILED"))
               .andExpect(jsonPath("$[0].configured").value(true))
               .andExpect(jsonPath("$[0].url").doesNotExist())
               .andExpect(jsonPath("$[1].configured").value(false))
               .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void sends_a_test_to_the_targets_following_tests() throws Exception {
        when(properties.environment()).thenReturn("preprod");
        when(notificationService.notify(any())).thenReturn(List.of(NotificationDispatchResult.delivered("essais-notifications")));

        mockMvc.perform(post("/admin/notifications/test"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].target").value("essais-notifications"))
               .andExpect(jsonPath("$[0].delivered").value(true));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).notify(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.TEST);
        assertThat(sent.getValue().fields()).contains(NotificationField.inline("Environnement", "preprod"));
    }

    @Test
    void sends_a_test_to_a_named_target() throws Exception {
        when(notificationService.notifyTarget(eq("alertes"), any()))
                .thenReturn(List.of(NotificationDispatchResult.failed("alertes", "404 Not Found")));

        mockMvc.perform(post("/admin/notifications/test").param("target", "alertes"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].delivered").value(false))
               .andExpect(jsonPath("$[0].error").value("404 Not Found"));
    }

    @Test
    void answers_404_for_an_unknown_target() throws Exception {
        when(notificationService.notifyTarget(eq("inconnue"), any()))
                .thenThrow(new NotificationTargetNotFoundException("inconnue"));

        mockMvc.perform(post("/admin/notifications/test").param("target", "inconnue"))
               .andExpect(status().isNotFound());
    }

    @Test
    void sends_the_daily_summary_on_demand() throws Exception {
        Notification summary = notification(NotificationType.DAILY_SUMMARY);
        when(dailySummaryService.today()).thenReturn(summary);
        when(notificationService.notify(summary)).thenReturn(List.of(NotificationDispatchResult.delivered("programme-du-jour")));

        mockMvc.perform(post("/admin/notifications/daily-summary"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].target").value("programme-du-jour"));
    }
}
