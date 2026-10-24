package fr.maximechazard.agorapilot.back.support;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @WebMvcTest} derrière la vraie chaîne de sécurité, avec un utilisateur
 * qui a tous les droits et le jeton CSRF sur chaque requête.
 * <p>
 * Ces tests vérifient le contrat HTTP d'un contrôleur ; les droits exigés par
 * chaque endpoint sont vérifiés à part, par {@code EndpointPermissionsTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@ActiveProfiles("dev")
@Import(ApiControllerTestConfig.class)
public @interface ApiControllerTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
