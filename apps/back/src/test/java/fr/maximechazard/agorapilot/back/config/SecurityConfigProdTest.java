package fr.maximechazard.agorapilot.back.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le profil prod doit verrouiller l'application exactement comme le profil
 * preprod : mêmes vérifications, rejouées avec {@code prod} actif.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agorapilot_prod_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "agorapilot.media.root=${java.io.tmpdir}/agorapilot-media-test",
})
@ActiveProfiles("prod")
class SecurityConfigProdTest extends SecurityConfigPreprodTest {
}
