package fr.maximechazard.agorapilot.back.account.admin;

import fr.maximechazard.agorapilot.back.account.AuthenticatedUser;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventDTO;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration des comptes et journal de sécurité : droit {@code users:manage}. */
@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class AccountAdminController {
    private final AccountAdminService accountAdminService;
    private final SecurityEventService securityEventService;

    // -------------------------------- GET --------------------------------

    @GetMapping("/users")
    public ResponseEntity<List<AccountDTO>> list() {
        return ResponseEntity.ok(accountAdminService.list());
    }

    /** Les événements les plus récents d'abord. */
    @GetMapping("/security-events")
    public ResponseEntity<List<SecurityEventDTO>> events(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(securityEventService.latest(limit));
    }

    // -------------------------------- POST --------------------------------

    @PostMapping("/users")
    public ResponseEntity<TemporaryPasswordDTO> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                                       @Valid @RequestBody CreateAccountRequest request) {
        return new ResponseEntity<>(accountAdminService.create(actor.id(), request), HttpStatus.CREATED);
    }

    @PostMapping("/users/{id}/disable")
    public ResponseEntity<AccountDTO> disable(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return ResponseEntity.ok(accountAdminService.disable(actor.id(), id));
    }

    /** Nouveau mot de passe provisoire ; réactive aussi un compte désactivé. */
    @PostMapping("/users/{id}/password-reset")
    public ResponseEntity<TemporaryPasswordDTO> resetPassword(@AuthenticationPrincipal AuthenticatedUser actor,
                                                              @PathVariable Long id) {
        return ResponseEntity.ok(accountAdminService.resetPassword(actor.id(), id));
    }

    // -------------------------------- PUT --------------------------------

    @PutMapping("/users/{id}/permissions")
    public ResponseEntity<AccountDTO> setPermissions(@AuthenticationPrincipal AuthenticatedUser actor,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody SetPermissionsRequest request) {
        return ResponseEntity.ok(accountAdminService.setPermissions(actor.id(), id, request));
    }
}
