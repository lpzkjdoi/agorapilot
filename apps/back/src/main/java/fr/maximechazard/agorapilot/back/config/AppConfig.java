package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.publication.SchedulingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
public class AppConfig {
}
