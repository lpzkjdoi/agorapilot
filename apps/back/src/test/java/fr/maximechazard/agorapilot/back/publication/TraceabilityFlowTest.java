package fr.maximechazard.agorapilot.back.publication;

import com.jayway.jsonpath.JsonPath;
import fr.maximechazard.agorapilot.back.account.AccountStatus;
import fr.maximechazard.agorapilot.back.account.Permission;
import fr.maximechazard.agorapilot.back.account.UserAccount;
import fr.maximechazard.agorapilot.back.account.UserAccountRepository;
import fr.maximechazard.agorapilot.back.account.events.SecurityEvent;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventRepository;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionRepository;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui a fait quoi, de bout en bout avec de vraies sessions : auteurs des
 * publications et des diffusions, et journal des actions qui ne laissent pas de
 * ligne derrière elles.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_traceability;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class TraceabilityFlowTest {

    private static final String PASSWORD = "correct horse battery";
    private static final LocalDate DAY = LocalDate.now().plusDays(3);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private PublicationOccurrenceRepository occurrenceRepository;
    @Autowired
    private PublicationDeliveryRepository deliveryRepository;
    @Autowired
    private SecurityEventRepository eventRepository;
    @Autowired
    private ChannelSuspensionRepository suspensionRepository;
    @Autowired
    private ChannelSuspensionService suspensionService;

    private MockHttpSession camille;
    private MockHttpSession bruno;

    @BeforeEach
    void setUp() throws Exception {
        deliveryRepository.deleteAll();
        occurrenceRepository.deleteAll();
        publicationRepository.deleteAll();
        suspensionRepository.deleteAll();
        accountRepository.deleteAll();
        camille = sessionOf("camille");
        bruno = sessionOf("bruno");
        eventRepository.deleteAll();
    }

    // ------------------------------------------------------------ helpers

    private MockHttpSession sessionOf(String username) throws Exception {
        accountRepository.save(new UserAccount(username, passwordEncoder.encode(PASSWORD), AccountStatus.ACTIVE,
                EnumSet.allOf(Permission.class), LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)));
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn().getRequest().getSession(false);
    }

    private Long createPublication(MockHttpSession session, String content) throws Exception {
        String body = mockMvc.perform(post("/api/publications").session(session).with(csrf())
                                     .contentType(MediaType.APPLICATION_JSON)
                                     .content("{\"content\":\"" + content + "\",\"status\":\"VERIFIED\"}"))
                             .andExpect(status().isCreated())
                             .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions schedule(MockHttpSession session, Long publicationId) throws Exception {
        return mockMvc.perform(post("/api/occurrences").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicationId\":" + publicationId + ",\"channels\":[\"FACEBOOK\"],\"date\":\"" + DAY + "\"}"));
    }

    private Long idOf(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private SecurityEvent lastEvent() {
        return eventRepository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, 1)).getFirst();
    }

    // ------------------------------------------------------------ tests

    @Test
    void records_who_created_and_who_last_modified_a_publication() throws Exception {
        Long id = createPublication(camille, "Marché de Noël samedi");

        mockMvc.perform(put("/api/publications/" + id).session(bruno).with(csrf())
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"content\":\"Marché de Noël dimanche\",\"status\":\"VERIFIED\"}"))
               .andExpect(jsonPath("$.createdBy").value("camille"))
               .andExpect(jsonPath("$.updatedBy").value("bruno"))
               .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void records_who_scheduled_a_delivery_and_keeps_it_when_the_day_is_spread_again() throws Exception {
        Long first = idOf(schedule(camille, createPublication(camille, "Marché de Noël samedi")).andExpect(status().isCreated()));
        idOf(schedule(bruno, createPublication(bruno, "Conseil municipal")).andExpect(status().isCreated()));

        // La seconde programmation a déplacé la première : son auteur ne change pas.
        assertThat(occurrenceRepository.findById(first).orElseThrow().getCreatedBy()).isEqualTo("camille");
        mockMvc.perform(get("/api/occurrences?from=" + DAY + "&to=" + DAY.plusDays(1)).session(bruno))
               .andExpect(jsonPath("$[0].createdBy").value("camille"))
               .andExpect(jsonPath("$[1].createdBy").value("bruno"));
    }

    @Test
    void journals_who_moved_and_who_cancelled_a_delivery() throws Exception {
        Long occurrence = idOf(schedule(camille, createPublication(camille, "Marché de Noël samedi")));

        mockMvc.perform(put("/api/occurrences/" + occurrence + "/schedule").session(bruno).with(csrf())
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"date\":\"" + DAY.plusDays(1) + "\",\"time\":\"17:30\"}"))
               .andExpect(status().isOk());

        SecurityEvent moved = lastEvent();
        assertThat(moved.getType()).isEqualTo(SecurityEventType.OCCURRENCE_RESCHEDULED);
        assertThat(moved.getActorUsername()).isEqualTo("bruno");
        assertThat(moved.getDetails()).startsWith("Diffusion " + occurrence + " de « Marché de Noël samedi »")
                                      .contains("→ " + DAY.plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " 17:30 (heure fixe)");

        mockMvc.perform(delete("/api/occurrences/" + occurrence).session(bruno).with(csrf()))
               .andExpect(status().isNoContent());

        SecurityEvent cancelled = lastEvent();
        assertThat(cancelled.getType()).isEqualTo(SecurityEventType.OCCURRENCE_CANCELLED);
        assertThat(cancelled.getActorUsername()).isEqualTo("bruno");
        assertThat(cancelled.getDetails()).contains("programmée par camille");
    }

    @Test
    void journals_who_retried_a_failed_delivery() throws Exception {
        Long occurrence = idOf(schedule(camille, createPublication(camille, "Marché de Noël samedi")));
        PublicationOccurrence failed = occurrenceRepository.findById(occurrence).orElseThrow();
        failed.setStatus(PublicationOccurrenceStatus.FAILED);
        occurrenceRepository.save(failed);
        deliveryRepository.findAllByOccurrence(failed).forEach(delivery -> {
            delivery.setStatus(DeliveryStatus.FAILED);
            deliveryRepository.save(delivery);
        });

        mockMvc.perform(post("/api/occurrences/" + occurrence + "/retry").session(bruno).with(csrf())
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"date\":\"" + DAY + "\"}"))
               .andExpect(status().isOk());

        assertThat(lastEvent().getType()).isEqualTo(SecurityEventType.OCCURRENCE_RETRIED);
        assertThat(lastEvent().getActorUsername()).isEqualTo("bruno");
    }

    @Test
    void journals_who_deleted_a_publication() throws Exception {
        Long id = createPublication(camille, "Une publication dont le texte est assez long pour être tronqué dans le journal");

        mockMvc.perform(delete("/api/publications/" + id).session(bruno).with(csrf())).andExpect(status().isNoContent());

        SecurityEvent deleted = lastEvent();
        assertThat(deleted.getType()).isEqualTo(SecurityEventType.PUBLICATION_DELETED);
        assertThat(deleted.getActorUsername()).isEqualTo("bruno");
        assertThat(deleted.getDetails()).startsWith("Publication " + id + " « Une publication").endsWith("… »");
    }

    /** Le canal est suspendu par l'ordonnanceur, sans acteur ; repris par quelqu'un. */
    @Test
    void journals_a_suspension_without_actor_and_who_resumed_the_channel() throws Exception {
        suspensionService.suspend(DeliveryChannel.FACEBOOK, "Confirmez votre identité (Facebook, code 368)");

        SecurityEvent suspended = lastEvent();
        assertThat(suspended.getType()).isEqualTo(SecurityEventType.CHANNEL_SUSPENDED);
        assertThat(suspended.getActorUsername()).isNull();
        assertThat(suspended.getDetails()).contains("code 368");

        mockMvc.perform(delete("/api/channels/FACEBOOK/suspension").session(camille).with(csrf()))
               .andExpect(status().is2xxSuccessful());

        assertThat(lastEvent().getType()).isEqualTo(SecurityEventType.CHANNEL_RESUMED);
        assertThat(lastEvent().getActorUsername()).isEqualTo("camille");
    }
}
