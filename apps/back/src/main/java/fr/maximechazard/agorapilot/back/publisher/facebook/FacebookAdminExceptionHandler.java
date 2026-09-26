package fr.maximechazard.agorapilot.back.publisher.facebook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduit les échecs des appels à l'API Graph en réponses exploitables.
 * <p>
 * Sans cela, une erreur pourtant explicite de Facebook — « The access token does
 * not belong to application X » — remonte en 500 accompagné d'une pile complète,
 * et il faut aller lire les logs du conteneur pour découvrir un message que
 * Facebook fournissait dès le départ.
 * <p>
 * Limité à {@link FacebookAdminController} pour ne pas capturer les exceptions
 * des autres contrôleurs.
 */
@RestControllerAdvice(assignableTypes = FacebookAdminController.class)
@RequiredArgsConstructor
@Slf4j
public class FacebookAdminExceptionHandler {

    private final ObjectMapper mapper;

    /**
     * Un 4xx de Facebook signale une requête ou des identifiants incorrects de
     * notre côté : on le répercute en 400. Un 5xx traduit une défaillance en
     * amont, d'où le 502.
     */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<FacebookError> handleGraphApiError(HttpStatusCodeException exception) {
        HttpStatusCode upstreamStatus = exception.getStatusCode();
        String body = exception.getResponseBodyAsString();

        HttpStatus status = upstreamStatus.is4xxClientError()
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;

        log.warn("Appel a l'API Graph en echec ({}) : {}", upstreamStatus, body);

        return ResponseEntity.status(status).body(parse(body, upstreamStatus.value()));
    }

    /**
     * Couvre notamment « No token available » (bootstrap jamais effectué) et
     * « Invalid token », qui décrivent un état incohérent plutôt qu'une requête
     * mal formée.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<FacebookError> handleInvalidState(IllegalStateException exception) {
        log.warn("Etat incoherent de l'integration Facebook : {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new FacebookError("facebook_invalid_state", exception.getMessage(), null, null));
    }

    /**
     * Extrait le message d'erreur de la réponse de Facebook, qui a la forme
     * {@code {"error":{"message":"...","type":"...","code":1}}}. En cas de corps
     * inattendu, on renvoie le corps brut : mieux vaut un message imparfait
     * qu'un message perdu.
     */
    private FacebookError parse(String body, int upstreamStatus) {
        try {
            JsonNode error = mapper.readTree(body).path("error");
            if (error.isMissingNode()) {
                return new FacebookError("facebook_api_error", body, null, upstreamStatus);
            }
            return new FacebookError(
                    "facebook_api_error",
                    error.path("message").asString(),
                    error.path("code").isMissingNode() ? null : error.path("code").asInt(),
                    upstreamStatus
            );
        } catch (RuntimeException e) {
            return new FacebookError("facebook_api_error", body, null, upstreamStatus);
        }
    }

    public record FacebookError(String error, String message, Integer facebookCode, Integer upstreamStatus) {
    }
}
