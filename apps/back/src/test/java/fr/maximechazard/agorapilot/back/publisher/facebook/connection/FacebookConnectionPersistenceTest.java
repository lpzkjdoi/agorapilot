package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les tables de la migration V5 contre une vraie base : un jeton de Page plus
 * long que 255 caractères, une échéance vide (jeton permanent), la purge des
 * demandes de connexion périmées.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_facebook_connection;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "agorapilot.scheduling.occurrences-delay=PT1H",
        "spring.jpa.show-sql=false"
})
@Transactional
class FacebookConnectionPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);

    @Autowired
    private FacebookConnectionRepository connections;
    @Autowired
    private FacebookOAuthStateRepository states;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        connections.deleteAll();
        states.deleteAll();
    }

    @Test
    void stores_a_long_permanent_page_token() {
        FacebookConnection connection = new FacebookConnection();
        connection.setPageId("page-id");
        connection.setPageName("Mairie");
        connection.setAccessToken("E".repeat(600));
        connection.setScopes("pages_show_list,pages_manage_posts");
        connection.setConnectedBy("maxime");
        connection.setConnectedAt(NOW);
        connection.setLastCheckResult(FacebookConnection.CheckResult.VALID);
        connections.saveAndFlush(connection);
        entityManager.clear();

        FacebookConnection stored = connections.findTopByOrderByConnectedAtDesc().orElseThrow();
        assertThat(stored.getAccessToken()).hasSize(600);
        assertThat(stored.getExpiresAt()).isNull();
        assertThat(stored.getLastCheckResult()).isEqualTo(FacebookConnection.CheckResult.VALID);
    }

    @Test
    void purges_the_requests_created_before_a_date() {
        states.save(new FacebookOAuthState("ancienne", "maxime", NOW.minusMinutes(30)));
        states.save(new FacebookOAuthState("recente", "maxime", NOW.minusMinutes(2)));
        states.flush();

        states.deleteCreatedBefore(NOW.minusMinutes(10));
        entityManager.clear();

        assertThat(states.findAll()).extracting(FacebookOAuthState::getState).containsExactly("recente");
    }
}
