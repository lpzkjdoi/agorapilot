package fr.maximechazard.agorapilot.back;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Racine de médiathèque jetable : sans elle le contexte de test écrirait
        // dans l'arbre de travail (apps/back/data/media).
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
        "spring.jpa.show-sql=false"
})
class AgorapilotBackApplicationTests {

    @Test
    void contextLoads() {
    }

}
