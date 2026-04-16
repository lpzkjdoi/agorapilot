package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FacebookProperties.class)
public class FacebookConfig {
    @Bean
    public RestClient facebookRestClient(FacebookProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.apiBaseUrl() + "/" + properties.apiVersion() + "/" + properties.pageId())
                .build();
    }
}