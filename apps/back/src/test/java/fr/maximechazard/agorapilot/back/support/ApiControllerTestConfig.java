package fr.maximechazard.agorapilot.back.support;

import fr.maximechazard.agorapilot.back.account.AuthService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Ce que {@link ApiControllerTest} ajoute au contexte d'un {@code @WebMvcTest}. */
@TestConfiguration
class ApiControllerTestConfig {

    /**
     * {@code SecurityConfig} en dépend pour son filtre de relecture des comptes,
     * qui n'est jamais sollicité ici (cf. {@link TestUsers}).
     */
    @Bean
    AuthService authService() {
        return mock(AuthService.class);
    }

    @Bean
    MockMvcBuilderCustomizer allPermissionsByDefault() {
        return builder -> builder.defaultRequest(get("/").with(TestUsers.allPermissions()));
    }
}
