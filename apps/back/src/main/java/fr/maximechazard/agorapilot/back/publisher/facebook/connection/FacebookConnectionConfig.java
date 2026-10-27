package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FacebookConnectionProperties.class)
public class FacebookConnectionConfig {
}
