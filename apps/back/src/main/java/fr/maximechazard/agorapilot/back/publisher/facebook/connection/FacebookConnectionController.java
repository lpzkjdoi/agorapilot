package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import fr.maximechazard.agorapilot.back.account.AuthenticatedUser;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.dtos.AuthorizationDTO;
import fr.maximechazard.agorapilot.back.publisher.facebook.connection.dtos.FacebookConnectionStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion de la Page Facebook depuis l'interface ({@code channels:manage}).
 * <p>
 * Sous {@code /api}, donc proxifié par le nginx du front, contrairement à
 * {@code /admin/facebook/**} : c'est ce qui rend la connexion possible sans
 * accès au VPS.
 */
@RestController
@RequestMapping("/api/channels/facebook")
@RequiredArgsConstructor
public class FacebookConnectionController {

    private final FacebookConnectionService connectionService;

    @GetMapping("/connection")
    public ResponseEntity<FacebookConnectionStatusDTO> status() {
        return ResponseEntity.ok(connectionService.status());
    }

    /** Ouvre une demande de connexion ; le front envoie le navigateur à l'adresse rendue. */
    @PostMapping("/connection")
    public ResponseEntity<AuthorizationDTO> start(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(new AuthorizationDTO(connectionService.start(user.username())));
    }

    /** Contrôle le jeton tout de suite, sans attendre le passage quotidien. */
    @PostMapping("/connection/check")
    public ResponseEntity<FacebookConnectionStatusDTO> check() {
        connectionService.check();
        return ResponseEntity.ok(connectionService.status());
    }

    /**
     * Retour de la boîte de dialogue Facebook. Public : la navigation vient de
     * facebook.com et le cookie de session ({@code SameSite=strict}) n'y est pas
     * joint. Le {@code state}, à usage unique, identifie la demande et son
     * auteur. Toujours une redirection vers la page du front, qui affiche l'issue.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error) {
        String target = connectionService.complete(state, code, error);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
    }
}
