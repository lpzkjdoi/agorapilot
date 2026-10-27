package fr.maximechazard.agorapilot.back.config;

import fr.maximechazard.agorapilot.back.account.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentUsernameAuditor")
public class JpaConfig {

    /** Remplit {@code @CreatedBy} et {@code @LastModifiedBy} avec le pseudo du compte connecté. */
    @Bean
    AuditorAware<String> currentUsernameAuditor() {
        return CurrentUser::username;
    }
}
