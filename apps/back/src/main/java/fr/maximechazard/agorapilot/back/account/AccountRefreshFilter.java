package fr.maximechazard.agorapilot.back.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Relit le compte de la session à chaque requête, avant toute décision d'accès.
 * <p>
 * Un compte désactivé, un mot de passe changé ailleurs ou une session trop
 * ancienne coupent la session sur-le-champ ; sinon, les droits exercés sont ceux
 * de la base à cet instant, pas ceux de la connexion. Retirer un droit prend donc
 * effet à la requête suivante.
 * <p>
 * Pas un {@code @Component} : Spring Boot l'enregistrerait aussi comme filtre de
 * servlet, hors de la chaîne de sécurité. Il est instancié par {@code SecurityConfig}.
 */
public class AccountRefreshFilter extends OncePerRequestFilter {
    private final AuthService authService;

    public AccountRefreshFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            Optional<UserAccount> account = authService.stillValid(user);

            if (account.isPresent()) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticationFor(user, account.get()));
                SecurityContextHolder.setContext(context);
            } else {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    /** Les autorités sont les codes des droits exercés ({@code publications:read}…). */
    public static UsernamePasswordAuthenticationToken authenticationFor(AuthenticatedUser user, UserAccount account) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, account.effectivePermissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.code()))
                .toList());
    }
}
