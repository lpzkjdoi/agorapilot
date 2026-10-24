package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.account.AccountStatus;
import fr.maximechazard.agorapilot.back.account.Permission;
import fr.maximechazard.agorapilot.back.account.UserAccount;
import fr.maximechazard.agorapilot.back.account.UserAccountRepository;
import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationMediaRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static fr.maximechazard.agorapilot.back.account.Permission.OCCURRENCES_SCHEDULE;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_PUBLISH;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_VERIFY;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Règles de vérification de bout en bout, avec de vraies sessions : qui peut
 * vérifier, ce qui repasse une publication en brouillon, et ce qu'un brouillon
 * ne peut pas faire.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_verification;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        // L'ordonnanceur ne doit pas balayer en parallèle du test.
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class PublicationVerificationFlowTest {

    private static final String PASSWORD = "correct horse battery";

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
    private PublicationMediaRepository publicationMediaRepository;
    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private PublicationDeliveryService deliveryService;

    /** Rédacteur : écrit, programme, diffuse, mais ne vérifie pas. */
    private MockHttpSession editor;
    /** Diffuseur : vérifie aussi. */
    private MockHttpSession verifier;

    @BeforeEach
    void setUp() throws Exception {
        deliveryRepository.deleteAll();
        occurrenceRepository.deleteAll();
        publicationMediaRepository.deleteAll();
        publicationRepository.deleteAll();
        mediaRepository.deleteAll();
        accountRepository.deleteAll();
        editor = sessionWith("redacteur", EnumSet.of(PUBLICATIONS_READ, PUBLICATIONS_WRITE, OCCURRENCES_SCHEDULE, PUBLICATIONS_PUBLISH));
        verifier = sessionWith("diffuseur", EnumSet.of(PUBLICATIONS_READ, PUBLICATIONS_WRITE, PUBLICATIONS_VERIFY));
    }

    // ------------------------------------------------------------ helpers

    private MockHttpSession sessionWith(String username, Set<Permission> permissions) throws Exception {
        accountRepository.save(new UserAccount(username, passwordEncoder.encode(PASSWORD), AccountStatus.ACTIVE,
                permissions, LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)));
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                                        .andExpect(status().isOk())
                                        .andReturn().getRequest().getSession(false);
    }

    private Publication publication(PublicationStatus status) {
        return publicationRepository.save(new Publication("Marché de Noël samedi", status));
    }

    private ResultActions create(MockHttpSession session, String status) throws Exception {
        return mockMvc.perform(post("/api/publications").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Marché de Noël\",\"status\":\"" + status + "\"}"));
    }

    private ResultActions update(MockHttpSession session, Long id, String content, String status) throws Exception {
        return mockMvc.perform(put("/api/publications/" + id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\",\"status\":\"" + status + "\"}"));
    }

    private PublicationStatus statusOf(Long id) {
        return publicationRepository.findById(id).orElseThrow().getStatus();
    }

    // ------------------------------------------------------------ vérifier

    @Nested
    class Verify {

        @Test
        void only_a_verifier_creates_a_verified_publication() throws Exception {
            create(editor, "VERIFIED").andExpect(status().isForbidden())
                                      .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vérifier")));
            assertThat(publicationRepository.findAll()).isEmpty();

            create(editor, "DRAFT").andExpect(status().isCreated());
            create(verifier, "VERIFIED").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        @Test
        void only_a_verifier_changes_the_status() throws Exception {
            Long draft = publication(PublicationStatus.DRAFT).getId();
            Long verified = publication(PublicationStatus.VERIFIED).getId();

            update(editor, draft, "Marché de Noël samedi", "VERIFIED").andExpect(status().isForbidden());
            update(editor, verified, "Marché de Noël samedi", "DRAFT").andExpect(status().isForbidden());
            assertThat(statusOf(draft)).isEqualTo(PublicationStatus.DRAFT);

            update(verifier, draft, "Marché de Noël samedi", "VERIFIED").andExpect(status().isOk());
            assertThat(statusOf(draft)).isEqualTo(PublicationStatus.VERIFIED);
        }

        /** Ce qui a été vérifié, c'est l'ancien texte. */
        @Test
        void a_text_changed_without_the_right_goes_back_to_draft() throws Exception {
            Long id = publication(PublicationStatus.VERIFIED).getId();

            update(editor, id, "Marché de Noël dimanche", "VERIFIED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void an_unchanged_text_keeps_the_verification() throws Exception {
            Long id = publication(PublicationStatus.VERIFIED).getId();

            update(editor, id, "Marché de Noël samedi", "VERIFIED").andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        @Test
        void a_verifier_keeps_the_verification_when_editing() throws Exception {
            Long id = publication(PublicationStatus.VERIFIED).getId();

            update(verifier, id, "Marché de Noël dimanche", "VERIFIED").andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        @Test
        void new_visuals_without_the_right_go_back_to_draft() throws Exception {
            Long id = publication(PublicationStatus.VERIFIED).getId();
            Long media = mediaRepository.save(new Media("2026/10/affiche.png", "affiche.png", MediaFileType.PNG, 100L, "cafe")).getId();

            mockMvc.perform(put("/api/publications/" + id + "/medias").session(editor).with(csrf())
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("{\"mediaIds\":[" + media + "]}"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.status").value("DRAFT"));
        }
    }

    // ------------------------------------------------------------ brouillon

    @Nested
    class Draft {

        @Test
        void cannot_be_scheduled() throws Exception {
            Long id = publication(PublicationStatus.DRAFT).getId();

            mockMvc.perform(post("/api/occurrences").session(editor).with(csrf())
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("{\"publicationId\":" + id + ",\"channels\":[\"FACEBOOK\"],\"date\":\""
                                   + LocalDate.now().plusDays(2) + "\"}"))
                   .andExpect(status().isConflict())
                   .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("n'est pas vérifiée")));
            assertThat(occurrenceRepository.findAll()).isEmpty();
        }

        @Test
        void cannot_be_published_immediately() throws Exception {
            Long id = publication(PublicationStatus.DRAFT).getId();

            mockMvc.perform(post("/api/publications/" + id + "/deliveries").session(editor).with(csrf())
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("{\"channel\":\"FACEBOOK\"}"))
                   .andExpect(status().isConflict());
            assertThat(occurrenceRepository.findAll()).isEmpty();
        }

        @Test
        void cannot_be_retried() throws Exception {
            Publication publication = publication(PublicationStatus.DRAFT);
            Long occurrence = occurrence(publication, LocalDateTime.now().minusDays(1), PublicationOccurrenceStatus.FAILED,
                    DeliveryStatus.FAILED);

            mockMvc.perform(post("/api/occurrences/" + occurrence + "/retry").session(editor).with(csrf())
                           .contentType(MediaType.APPLICATION_JSON)
                           .content("{\"date\":\"" + LocalDate.now().plusDays(2) + "\"}"))
                   .andExpect(status().isConflict());
        }

        /** Programmée vérifiée, puis repassée en brouillon : elle ne part pas. */
        @Test
        void is_not_delivered_when_it_went_back_to_draft_after_scheduling() {
            Publication publication = publication(PublicationStatus.DRAFT);
            Long occurrence = occurrence(publication, LocalDateTime.now().minusMinutes(1), PublicationOccurrenceStatus.SCHEDULED,
                    DeliveryStatus.PENDING);

            deliveryService.publishScheduledOccurrence(occurrence);

            PublicationDelivery delivery = deliveryRepository.findAll().getFirst();
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(delivery.getErrorMessage()).contains("n'est plus vérifiée");
            assertThat(occurrenceRepository.findById(occurrence).orElseThrow().getStatus())
                    .isEqualTo(PublicationOccurrenceStatus.FAILED);
        }

        private Long occurrence(Publication publication, LocalDateTime scheduledAt, PublicationOccurrenceStatus status,
                                DeliveryStatus deliveryStatus) {
            PublicationOccurrence occurrence = new PublicationOccurrence();
            occurrence.setPublication(publication);
            occurrence.setScheduledAt(scheduledAt);
            occurrence.setStatus(status);
            PublicationDelivery delivery = new PublicationDelivery();
            delivery.setChannel(DeliveryChannel.FACEBOOK);
            delivery.setStatus(deliveryStatus);
            occurrence.addDelivery(delivery);
            return occurrenceRepository.save(occurrence).getId();
        }
    }
}
