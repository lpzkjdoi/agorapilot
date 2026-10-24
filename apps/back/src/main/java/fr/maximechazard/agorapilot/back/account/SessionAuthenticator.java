package fr.maximechazard.agorapilot.back.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Ouvre la session d'un compte, toujours dans une session neuve. */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {
    private final SecurityContextRepository securityContextRepository;

    /**
     * La session précédente est détruite et une autre est créée : un identifiant
     * de session connu avant la connexion ne vaut plus rien après (fixation de
     * session).
     */
    public void open(UserAccount account, LocalDateTime authenticatedAt,
                     HttpServletRequest request, HttpServletResponse response) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true);

        AuthenticatedUser user = new AuthenticatedUser(account.getId(), account.getUsername(), authenticatedAt);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(AccountRefreshFilter.authenticationFor(user, account));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
