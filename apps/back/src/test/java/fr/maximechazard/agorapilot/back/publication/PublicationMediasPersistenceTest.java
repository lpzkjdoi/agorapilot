package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationMediaRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rattachement des visuels contre une vraie base.
 * <p>
 * Les tests unitaires du service ne peuvent pas voir ce qui est vérifié ici : la
 * contrainte d'unicité {@code (publication_id, media_id)} et l'ordre dans lequel
 * Hibernate émet ses INSERT et ses DELETE ne se manifestent qu'au flush.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_publication_medias;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "spring.jpa.show-sql=false"
})
class PublicationMediasPersistenceTest {

    @Autowired
    private PublicationService publicationService;
    @Autowired
    private PublicationRepository publicationRepository;
    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private PublicationMediaRepository publicationMediaRepository;
    @Autowired
    private EntityManager entityManager;

    private Long publicationId;
    private Long first;
    private Long second;
    private Long third;

    @BeforeEach
    void setUp() {
        publicationMediaRepository.deleteAll();
        publicationRepository.deleteAll();
        mediaRepository.deleteAll();

        publicationId = publicationRepository.save(new Publication("Marché de producteurs", PublicationStatus.DRAFT)).getId();
        first = saveMedia("un");
        second = saveMedia("deux");
        third = saveMedia("trois");
    }

    @Test
    void attaches_medias_in_order() {
        PublicationDTO dto = publicationService.setMedias(publicationId, List.of(second, first));

        assertThat(dto.medias()).extracting(media -> media.id()).containsExactly(second, first);
    }

    @Test
    void reorders_an_existing_selection_without_tripping_the_unique_constraint() {
        // Le cas qui cassait : réordonner sans changer la composition faisait
        // recréer les mêmes couples (publication, média), et Hibernate émet les
        // INSERT avant les DELETE.
        publicationService.setMedias(publicationId, List.of(first, second));

        PublicationDTO dto = publicationService.setMedias(publicationId, List.of(second, first));

        assertThat(dto.medias()).extracting(media -> media.id()).containsExactly(second, first);
        assertThat(publicationMediaRepository.count()).isEqualTo(2);
    }

    /**
     * Transactionnel, et le contexte de persistance est vidé avant la relecture :
     * sans cela `findById` rendrait l'instance déjà en mémoire et le test ne
     * dirait rien de ce que la base contient réellement.
     */
    @Test
    @Transactional
    void keeps_the_order_after_a_reload_from_the_database() {
        publicationService.setMedias(publicationId, List.of(third, first, second));

        entityManager.flush();
        entityManager.clear();

        Publication reloaded = publicationRepository.findById(publicationId).orElseThrow();

        assertThat(reloaded.getMedias())
                .extracting(attachment -> attachment.getMedia().getId())
                .containsExactly(third, first, second);
    }

    @Test
    void adds_and_removes_in_the_same_call() {
        publicationService.setMedias(publicationId, List.of(first, second));

        PublicationDTO dto = publicationService.setMedias(publicationId, List.of(second, third));

        assertThat(dto.medias()).extracting(media -> media.id()).containsExactly(second, third);
        assertThat(publicationMediaRepository.count()).isEqualTo(2);
    }

    @Test
    void detaches_everything_without_deleting_the_medias() {
        publicationService.setMedias(publicationId, List.of(first, second));

        assertThat(publicationService.setMedias(publicationId, List.of()).medias()).isEmpty();
        assertThat(publicationMediaRepository.count()).isZero();
        // La médiathèque est une bibliothèque partagée : les visuels restent.
        assertThat(mediaRepository.count()).isEqualTo(3);
    }

    @Test
    void is_idempotent_when_called_twice_with_the_same_list() {
        publicationService.setMedias(publicationId, List.of(first, second));
        PublicationDTO dto = publicationService.setMedias(publicationId, List.of(first, second));

        assertThat(dto.medias()).extracting(media -> media.id()).containsExactly(first, second);
        assertThat(publicationMediaRepository.count()).isEqualTo(2);
    }

    private Long saveMedia(String name) {
        return mediaRepository.save(
                new Media("2026/08/" + name + ".png", name + ".png", MediaFileType.PNG, 100L, "cafe-" + name)).getId();
    }
}
