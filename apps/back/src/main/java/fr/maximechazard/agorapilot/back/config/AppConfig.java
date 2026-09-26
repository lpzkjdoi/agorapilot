package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.publication.SchedulingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
public class AppConfig {

    /**
     * Horloge du fuseau du conteneur ({@code TZ}). Injectée plutôt qu'appelée en
     * dur là où l'heure décide d'un comportement (fenêtre de publication du
     * jour), pour que les tests puissent la figer.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
