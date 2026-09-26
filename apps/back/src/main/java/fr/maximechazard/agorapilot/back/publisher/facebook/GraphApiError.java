package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Erreur renvoyée par l'API Graph, lue dans le corps de sa réponse :
 * {@code {"error":{"message":"…","type":"OAuthException","code":368,"error_subcode":4854002,"error_user_msg":"…"}}}.
 * <p>
 * Le corps complet est un JSON de plusieurs centaines de caractères
 * ({@code sentry_block_data}, {@code fbtrace_id}…) : utile au support de Meta,
 * illisible pour qui cherche pourquoi un post n'est pas parti.
 *
 * @param message     message technique, toujours présent en pratique
 * @param code        code d'erreur Graph ({@code 190} jeton invalide, {@code 368} action bloquée…)
 * @param subcode     précision du code, quand Facebook en donne une
 * @param userMessage message destiné à l'utilisateur, souvent vide
 */
public record GraphApiError(String message, Integer code, Integer subcode, String userMessage) {

    /**
     * Vide quand le corps n'a pas la forme attendue — page HTML d'un proxy,
     * corps vide : à l'appelant de se rabattre sur le corps brut.
     */
    public static Optional<GraphApiError> parse(String body, ObjectMapper mapper) {
        try {
            JsonNode error = mapper.readTree(body).path("error");

            if (!error.isObject()) {
                return Optional.empty();
            }

            return Optional.of(new GraphApiError(
                    text(error, "message"),
                    integer(error, "code"),
                    integer(error, "error_subcode"),
                    text(error, "error_user_msg")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Le motif à montrer : le message destiné à l'utilisateur quand Facebook en
     * fournit un, sinon le message technique, suivi de la source et des codes
     * qui permettent de retrouver l'erreur dans la documentation Graph.
     * <p>
     * {@code Confirmez votre identité… (Facebook, code 368, sous-code 4854002)}
     */
    public String describe() {
        String text = StringUtils.hasText(userMessage) ? userMessage
                : StringUtils.hasText(message) ? message
                : "Erreur sans message";

        if (code == null) {
            return text + " (Facebook)";
        }

        String codes = subcode == null ? "code " + code : "code " + code + ", sous-code " + subcode;

        return text + " (Facebook, " + codes + ")";
    }

    private static String text(JsonNode error, String field) {
        JsonNode node = error.path(field);
        return node.isString() ? node.asString() : null;
    }

    private static Integer integer(JsonNode error, String field) {
        JsonNode node = error.path(field);
        return node.isNumber() ? node.asInt() : null;
    }
}
