package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.media.publishing.PublishableImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Les trois chemins de publication de l'API Graph.
 * <p>
 * Le point à ne pas régresser : le binaire est <strong>envoyé</strong> en
 * multipart. Désigner l'image par une URL demanderait aux serveurs de Facebook de
 * venir la chercher, ce qu'ils ne peuvent pas faire — nos environnements sont
 * fermés au réseau WireGuard par Traefik.
 */
class FacebookClientTest {

    private static final FacebookProperties PROPS =
            new FacebookProperties("client-id", "client-secret", "page-id", "https://graph.facebook.com", "v25.0");

    private static final String FEED = "https://graph.facebook.com/v25.0/page-id/feed";
    private static final String PHOTOS = "https://graph.facebook.com/v25.0/page-id/photos";

    private MockRestServiceServer server;
    private FacebookClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                                               .baseUrl(PROPS.apiBaseUrl() + "/" + PROPS.apiVersion());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FacebookClient(builder.build(), PROPS, new ObjectMapper());
    }

    @Test
    void posts_plain_text_to_the_feed_when_there_is_no_image() {
        server.expect(requestTo(FEED))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.message").value("Marché samedi"))
              .andExpect(jsonPath("$.access_token").value("page-token"))
              .andRespond(withSuccess("{\"id\":\"123_456\"}", MediaType.APPLICATION_JSON));

        assertThat(client.publish("Marché samedi", List.of(), "page-token").id()).isEqualTo("123_456");
        server.verify();
    }

    @Test
    void publishes_a_single_image_directly_through_the_photos_endpoint() {
        // Une seule photo : un dépôt non publié suivi d'un post au fil produirait
        // un rendu différent et un appel de plus pour rien.
        server.expect(requestTo(PHOTOS))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"source\"")))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"affiche.png\"")))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("Marché samedi")))
              .andRespond(withSuccess("{\"id\":\"789\"}", MediaType.APPLICATION_JSON));

        assertThat(client.publish("Marché samedi", List.of(image("affiche.png")), "page-token").id())
                .isEqualTo("789");
        server.verify();
    }

    @Test
    void sends_the_alt_text_as_the_photo_caption() {
        server.expect(requestTo(PHOTOS))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("alt_text_custom")))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("Affiche du marché")))
              .andRespond(withSuccess("{\"id\":\"789\"}", MediaType.APPLICATION_JSON));

        client.publish("Marché", List.of(
                new PublishableImage("a.png", "image/png", "Affiche du marché", bytes())), "page-token");
        server.verify();
    }

    @Test
    void omits_the_caption_when_no_alt_text_was_entered() {
        server.expect(requestTo(PHOTOS))
              .andExpect(content().string(org.hamcrest.Matchers.not(
                      org.hamcrest.Matchers.containsString("alt_text_custom"))))
              .andRespond(withSuccess("{\"id\":\"789\"}", MediaType.APPLICATION_JSON));

        client.publish("Marché", List.of(image("a.png")), "page-token");
        server.verify();
    }

    @Test
    void uploads_each_photo_unpublished_then_attaches_them_to_a_feed_post() {
        server.expect(times(2), requestTo(PHOTOS))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
              .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"published\"")))
              .andRespond(withSuccess("{\"id\":\"photo-1\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(FEED))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.message").value("Marché samedi"))
              .andExpect(jsonPath("$.attached_media.length()").value(2))
              .andExpect(jsonPath("$.attached_media[0].media_fbid").value("photo-1"))
              .andRespond(withSuccess("{\"id\":\"123_999\"}", MediaType.APPLICATION_JSON));

        assertThat(client.publish("Marché samedi", List.of(image("une.png"), image("deux.png")), "page-token").id())
                .isEqualTo("123_999");
        server.verify();
    }

    @Test
    void never_sends_a_url_instead_of_the_binary() {
        // Garde-fou explicite : les serveurs de Facebook ne peuvent pas atteindre
        // nos environnements, fermés au réseau WireGuard.
        server.expect(requestTo(PHOTOS))
              .andExpect(content().string(org.hamcrest.Matchers.not(
                      org.hamcrest.Matchers.containsString("name=\"url\""))))
              .andRespond(withSuccess("{\"id\":\"789\"}", MediaType.APPLICATION_JSON));

        client.publish("Marché", List.of(image("a.png")), "page-token");
        server.verify();
    }

    private PublishableImage image(String filename) {
        return new PublishableImage(filename, "image/png", null, bytes());
    }

    private byte[] bytes() {
        return "binaire".getBytes(StandardCharsets.UTF_8);
    }
}
