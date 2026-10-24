package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.dtos.CurrentUserDTO;
import fr.maximechazard.agorapilot.back.account.exceptions.AuthenticationFailedException;
import fr.maximechazard.agorapilot.back.account.requests.ChangePasswordRequest;
import fr.maximechazard.agorapilot.back.account.requests.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Connexion et compte courant. La déconnexion ({@code POST /api/auth/logout})
 * est servie par Spring Security, cf. {@code SecurityConfig}.
 */
@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class AuthController {
    private final AuthService authService;
    private final UserAccountRepository repository;
    private final SessionAuthenticator sessionAuthenticator;

    // -------------------------------- POST --------------------------------

    @PostMapping("/auth/login")
    public ResponseEntity<CurrentUserDTO> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserAccount account = authService.authenticate(request.getUsername(), request.getPassword());
        sessionAuthenticator.open(account, authService.now(), httpRequest, httpResponse);
        return ResponseEntity.ok(toDTO(account));
    }

    // -------------------------------- GET --------------------------------

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(toDTO(findAccount(user)));
    }

    // -------------------------------- PUT --------------------------------

    /**
     * Change le mot de passe. Les autres sessions du compte sont coupées ; celle-ci
     * est renouvelée et reste ouverte.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LocalDateTime changedAt = authService.changePassword(user.id(), request.getCurrentPassword(), request.getNewPassword());
        sessionAuthenticator.open(findAccount(user), changedAt, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    private UserAccount findAccount(AuthenticatedUser user) {
        return repository.findById(user.id()).orElseThrow(AuthenticationFailedException::new);
    }

    private static CurrentUserDTO toDTO(UserAccount account) {
        return new CurrentUserDTO(account.getUsername(), account.getStatus(), account.effectivePermissions().stream()
                .sorted(Comparator.naturalOrder())
                .map(Permission::code)
                .toList());
    }
}
