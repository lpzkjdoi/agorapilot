package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.account.AccountRefreshFilter;
import fr.maximechazard.agorapilot.back.account.AuthService;
import fr.maximechazard.agorapilot.back.account.SecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static fr.maximechazard.agorapilot.back.account.Permission.CAMPAIGNS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.CAMPAIGNS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.CHANNELS_MANAGE;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_DELETE;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.MEDIAS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.OCCURRENCES_SCHEDULE;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_DELETE;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_PUBLISH;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_READ;
import static fr.maximechazard.agorapilot.back.account.Permission.PUBLICATIONS_WRITE;
import static fr.maximechazard.agorapilot.back.account.Permission.USERS_MANAGE;

/**
 * Une seule chaîne, pour tous les profils : tout est refusé sauf ce qui est
 * explicitement permis ici (cf. docs/proposals/2026-10-24-comptes-et-droits.md).
 * <p>
 * Un endpoint ajouté sans règle est donc fermé, jamais ouvert. La table des droits
 * vit en un seul endroit, ci-dessous, et {@code EndpointPermissionsTest} la vérifie
 * endpoint par endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService,
                                            SecurityContextRepository securityContextRepository,
                                            Environment environment) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Jeton dans le cookie XSRF-TOKEN, renvoyé par le HttpClient d'Angular
                // dans l'en-tête X-XSRF-TOKEN sur toute requête qui modifie quelque chose.
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // Pas de redirection vers une page de connexion : c'est une API, le
                // front décide quoi faire d'un 401.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new AccountRefreshFilter(authService), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> authorize(auth, dev));

        return http.build();
    }

    private static void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
                                  boolean dev) {
        // ---- Public
        auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            // Healthcheck du conteneur, dont dépend le démarrage du front.
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll();

        if (dev) {
            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
        }

        // ---- Tout compte connecté, même avec un mot de passe provisoire
        auth.requestMatchers(HttpMethod.GET, "/api/me").authenticated()
            .requestMatchers(HttpMethod.PUT, "/api/me/password").authenticated()
            .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()

            // ---- Publications et diffusions
            .requestMatchers(HttpMethod.GET, "/api/publications").hasAuthority(PUBLICATIONS_READ.code())
            .requestMatchers(HttpMethod.POST, "/api/publications").hasAuthority(PUBLICATIONS_WRITE.code())
            .requestMatchers(HttpMethod.PUT, "/api/publications/*", "/api/publications/*/medias",
                    "/api/publications/*/campaign").hasAuthority(PUBLICATIONS_WRITE.code())
            .requestMatchers(HttpMethod.DELETE, "/api/publications/*").hasAuthority(PUBLICATIONS_DELETE.code())
            .requestMatchers(HttpMethod.POST, "/api/publications/*/deliveries").hasAuthority(PUBLICATIONS_PUBLISH.code())

            .requestMatchers(HttpMethod.GET, "/api/occurrences", "/api/occurrences/weekly").hasAuthority(PUBLICATIONS_READ.code())
            .requestMatchers(HttpMethod.POST, "/api/occurrences", "/api/occurrences/*/retry").hasAuthority(OCCURRENCES_SCHEDULE.code())
            .requestMatchers(HttpMethod.PUT, "/api/occurrences/*/schedule").hasAuthority(OCCURRENCES_SCHEDULE.code())
            .requestMatchers(HttpMethod.DELETE, "/api/occurrences/*").hasAuthority(OCCURRENCES_SCHEDULE.code())

            .requestMatchers(HttpMethod.GET, "/api/dashboard").hasAuthority(PUBLICATIONS_READ.code())

            // ---- Canaux
            .requestMatchers(HttpMethod.GET, "/api/channels/suspensions").hasAuthority(PUBLICATIONS_READ.code())
            .requestMatchers(HttpMethod.DELETE, "/api/channels/*/suspension").hasAuthority(CHANNELS_MANAGE.code())
            .requestMatchers("/admin/**").hasAuthority(CHANNELS_MANAGE.code())

            // ---- Campagnes
            .requestMatchers(HttpMethod.GET, "/api/campaigns").hasAuthority(CAMPAIGNS_READ.code())
            .requestMatchers(HttpMethod.POST, "/api/campaigns", "/api/campaigns/*/closure").hasAuthority(CAMPAIGNS_WRITE.code())

            // ---- Médiathèque
            .requestMatchers(HttpMethod.GET, "/api/medias", "/api/medias/*", "/api/medias/*/file").hasAuthority(MEDIAS_READ.code())
            .requestMatchers(HttpMethod.POST, "/api/medias").hasAuthority(MEDIAS_WRITE.code())
            .requestMatchers(HttpMethod.PATCH, "/api/medias/*").hasAuthority(MEDIAS_WRITE.code())
            .requestMatchers(HttpMethod.DELETE, "/api/medias/*").hasAuthority(MEDIAS_DELETE.code())

            // ---- Comptes et journal de sécurité
            .requestMatchers(HttpMethod.GET, "/api/users", "/api/security-events").hasAuthority(USERS_MANAGE.code())
            .requestMatchers(HttpMethod.POST, "/api/users", "/api/users/*/disable", "/api/users/*/password-reset")
                .hasAuthority(USERS_MANAGE.code())
            .requestMatchers(HttpMethod.PUT, "/api/users/*/permissions").hasAuthority(USERS_MANAGE.code())

            // ---- Tout le reste
            .anyRequest().denyAll();
    }

    /** BCrypt, coût 12 : environ un quart de seconde par vérification. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
