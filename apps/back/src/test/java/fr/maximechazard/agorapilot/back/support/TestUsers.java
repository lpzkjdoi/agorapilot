package fr.maximechazard.agorapilot.back.support;

import fr.maximechazard.agorapilot.back.account.Permission;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Utilisateurs simulés pour MockMvc. Leur principal n'est pas un
 * {@code AuthenticatedUser} : {@code AccountRefreshFilter} les laisse passer sans
 * relire de compte en base.
 */
public final class TestUsers {

    private TestUsers() {
    }

    /** Un utilisateur qui détient exactement ces droits, avec le jeton CSRF. */
    public static RequestPostProcessor allowedTo(Permission... permissions) {
        RequestPostProcessor user = user("test").authorities(Arrays.stream(permissions)
                .map(permission -> new SimpleGrantedAuthority(permission.code()))
                .toList());
        return request -> csrf().postProcessRequest(user.postProcessRequest(request));
    }

    /** Tous les droits : pour tester un contrôleur, pas les droits eux-mêmes. */
    public static RequestPostProcessor allPermissions() {
        return allowedTo(Permission.values());
    }
}
