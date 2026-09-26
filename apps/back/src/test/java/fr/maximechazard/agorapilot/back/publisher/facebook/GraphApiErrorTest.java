package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GraphApiErrorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reads_the_message_and_the_codes_of_a_graph_error() {
        String body = """
                {"error":{"message":"Confirmez votre identit\\u00e9.","type":"OAuthException","code":368,\
                "error_subcode":4854002,"error_user_msg":"","fbtrace_id":"AXp8"}}""";

        assertThat(GraphApiError.parse(body, mapper)).hasValue(
                new GraphApiError("Confirmez votre identité.", 368, 4854002, ""));
    }

    @Test
    void describes_the_error_with_its_codes() {
        assertThat(new GraphApiError("Confirmez votre identité.", 368, 4854002, "").describe())
                .isEqualTo("Confirmez votre identité. (Facebook, code 368, sous-code 4854002)");
        assertThat(new GraphApiError("Error validating access token", 190, null, null).describe())
                .isEqualTo("Error validating access token (Facebook, code 190)");
    }

    /** Graph réserve `error_user_msg` à ce qu'il faut montrer à l'utilisateur. */
    @Test
    void prefers_the_message_meant_for_the_user() {
        GraphApiError error = new GraphApiError("(#100) Invalid parameter", 100, 1363030, "Le fichier est trop volumineux.");

        assertThat(error.describe()).isEqualTo("Le fichier est trop volumineux. (Facebook, code 100, sous-code 1363030)");
    }

    @Test
    void still_describes_an_error_without_message() {
        assertThat(new GraphApiError(null, null, null, null).describe()).isEqualTo("Erreur sans message (Facebook)");
    }

    @Test
    void finds_nothing_in_a_body_that_is_not_a_graph_error() {
        assertThat(GraphApiError.parse("<html>502 Bad Gateway</html>", mapper)).isEmpty();
        assertThat(GraphApiError.parse("{\"id\":\"123\"}", mapper)).isEmpty();
        assertThat(GraphApiError.parse("{\"error\":\"oops\"}", mapper)).isEmpty();
        assertThat(GraphApiError.parse("", mapper)).isEmpty();
    }
}
