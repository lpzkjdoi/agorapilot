package fr.maximechazard.agorapilot.back.campaign;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cycle de vie d'une campagne contre une vraie base.
 * <p>
 * Les tests unitaires du service ne peuvent pas voir ce qui est vérifié ici :
 * la nullité de {@code end_date} et les contraintes temporelles de l'entité ne
 * se manifestent qu'au flush.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_campaign;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "spring.jpa.show-sql=false"
})
class CampaignPersistenceTest {

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private CampaignService campaignService;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        campaignRepository.deleteAll();
    }

    @Test
    @Transactional
    void persisteUneCampagneADureeIndeterminee() {
        Campaign campaign = new Campaign(
                "Budget participatif",
                "Appel à projets permanent",
                LocalDateTime.now().plusDays(1),
                null
        );

        Long id = campaignRepository.save(campaign).getId();
        entityManager.flush();
        entityManager.clear();

        Campaign reloaded = campaignRepository.findById(id).orElseThrow();
        assertThat(reloaded.getEndDate()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
    }

    @Test
    @Transactional
    void persisteUneCampagneSansAucuneDate() {
        Campaign campaign = new Campaign("Brouillon", "Sans calendrier", null, null);

        Long id = campaignRepository.save(campaign).getId();
        entityManager.flush();
        entityManager.clear();

        Campaign reloaded = campaignRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStartDate()).isNull();
        assertThat(reloaded.getEndDate()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.DRAFT);
    }

    /**
     * Une campagne vieillit : sa date de début finit par être dans le passé. La
     * modifier ne doit pas pour autant devenir impossible.
     */
    @Test
    @Transactional
    void metAJourUneCampagneDejaCommencee() {
        Long id = campaignRepository.save(
                new Campaign("En cours", "Démarrée hier", LocalDateTime.now().plusDays(1), null)
        ).getId();
        entityManager.flush();

        // Le temps passe : on recule les dates en base, ce que le constructeur
        // ne permet pas de faire directement.
        entityManager.createNativeQuery(
                "UPDATE campaigns SET start_date = ?, end_date = ? WHERE id = ?")
                     .setParameter(1, LocalDateTime.now().minusDays(10))
                     .setParameter(2, LocalDateTime.now().plusDays(10))
                     .setParameter(3, id)
                     .executeUpdate();
        entityManager.clear();

        Campaign reloaded = campaignRepository.findById(id).orElseThrow();
        reloaded.setStatus(CampaignStatus.COMPLETED);

        assertThatCode(() -> {
            campaignRepository.save(reloaded);
            entityManager.flush();
        }).doesNotThrowAnyException();
    }

    /**
     * La clôture complète, contre une vraie base : c'est elle qui butait sur les
     * contraintes temporelles de l'entité, en écrivant une date de fin au présent
     * sur une campagne dont la date de début était passée.
     */
    @Test
    @Transactional
    void clotureUneCampagneCommencee() {
        Long id = campaignRepository.save(
                new Campaign("En cours", "À durée indéterminée", LocalDateTime.now().plusDays(1), null)
        ).getId();
        entityManager.flush();

        entityManager.createNativeQuery("UPDATE campaigns SET start_date = ? WHERE id = ?")
                     .setParameter(1, LocalDateTime.now().minusDays(10))
                     .setParameter(2, id)
                     .executeUpdate();
        entityManager.clear();

        campaignService.close(id);
        entityManager.flush();
        entityManager.clear();

        Campaign closed = campaignRepository.findById(id).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(closed.getEndDate()).isNotNull();
    }

    /** Clôturer, c'est arrêter la campagne maintenant : la date de fin devient le présent. */
    @Test
    @Transactional
    void accepteUneDateDeFinAuPresent() {
        Long id = campaignRepository.save(
                new Campaign("En cours", "Démarrée hier", LocalDateTime.now().plusDays(1), null)
        ).getId();
        entityManager.flush();
        entityManager.clear();

        Campaign reloaded = campaignRepository.findById(id).orElseThrow();
        reloaded.setEndDate(LocalDateTime.now());
        reloaded.setStatus(CampaignStatus.COMPLETED);

        assertThatCode(() -> {
            campaignRepository.save(reloaded);
            entityManager.flush();
        }).doesNotThrowAnyException();
    }
}
