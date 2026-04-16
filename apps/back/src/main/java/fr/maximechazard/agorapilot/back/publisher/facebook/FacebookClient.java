package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publication.Publication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FacebookClient {
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public FacebookPostResponse publish(Publication publication, String token) {
        FacebookPostRequest request = new FacebookPostRequest(publication.getContent(), "", "", token);

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("feed")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        return mapper.readValue(response, FacebookPostResponse.class);
    }
}
