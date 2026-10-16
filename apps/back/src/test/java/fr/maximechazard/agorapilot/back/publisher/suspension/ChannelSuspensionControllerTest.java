package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChannelSuspensionController.class)
@ActiveProfiles("dev")
class ChannelSuspensionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelSuspensionService channelSuspensionService;

    @Test
    void lists_the_suspended_channels() throws Exception {
        when(channelSuspensionService.findAll()).thenReturn(List.of(new ChannelSuspensionDTO(
                DeliveryChannel.FACEBOOK, "Confirmez votre identité (Facebook, code 368)",
                LocalDateTime.of(2026, 10, 16, 14, 5))));

        mockMvc.perform(get("/api/channels/suspensions"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].channel").value("FACEBOOK"))
               .andExpect(jsonPath("$[0].reason").value("Confirmez votre identité (Facebook, code 368)"))
               .andExpect(jsonPath("$[0].suspendedAt").value("2026-10-16T14:05:00"));
    }

    @Test
    void returns_an_empty_list_when_every_channel_works() throws Exception {
        when(channelSuspensionService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/channels/suspensions"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lifts_the_suspension_of_a_channel() throws Exception {
        mockMvc.perform(delete("/api/channels/FACEBOOK/suspension"))
               .andExpect(status().isNoContent());

        verify(channelSuspensionService).lift(DeliveryChannel.FACEBOOK);
    }

    @Test
    void rejects_an_unknown_channel() throws Exception {
        mockMvc.perform(delete("/api/channels/TWITTER/suspension"))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(channelSuspensionService);
    }
}
