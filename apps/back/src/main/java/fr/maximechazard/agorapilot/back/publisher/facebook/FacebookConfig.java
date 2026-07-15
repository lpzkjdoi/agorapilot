package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FacebookProperties.class)
public class FacebookConfig {

    @Bean
    public ClientHttpRequestFactory facebookClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    public RestClient facebookRestClient(FacebookProperties properties, ClientHttpRequestFactory facebookClientHttpRequestFactory) {
        return RestClient.builder()
                .baseUrl(properties.apiBaseUrl() + "/" + properties.apiVersion())
                .requestFactory(facebookClientHttpRequestFactory)
                .build();
    }
}
