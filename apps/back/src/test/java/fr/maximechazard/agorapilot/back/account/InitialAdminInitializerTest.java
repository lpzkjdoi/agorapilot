package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialAdminInitializerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);

    private UserAccountRepository repository;
    private AuthService authService;
    private SecurityEventService events;
    /** Coût minimal : seul le résultat compte ici, pas la lenteur. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        authService = mock(AuthService.class);
        events = mock(SecurityEventService.class);
        when(authService.now()).thenReturn(NOW);
    }

    private InitialAdminInitializer initializer(String username, String password) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.InitialAdmin(username, password), 5, Duration.ofMinutes(15), Duration.ofHours(12));
        return new InitialAdminInitializer(repository, encoder, properties, authService, events);
    }

    @Test
    void creates_a_pending_administrator_with_every_permission_when_there_is_no_account() {
        when(repository.count()).thenReturn(0L);

        initializer("  Maxime ", "un mot de passe provisoire").run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        UserAccount account = saved.getValue();
        assertThat(account.getUsername()).isEqualTo("maxime");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_PASSWORD);
        assertThat(account.getPermissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(encoder.matches("un mot de passe provisoire", account.getPasswordHash())).isTrue();
        assertThat(account.getPasswordChangedAt()).isEqualTo(NOW);
    }

    @Test
    void does_nothing_once_an_account_exists() {
        when(repository.count()).thenReturn(1L);

        initializer("maxime", "un mot de passe provisoire").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void starts_without_account_when_nothing_is_configured() {
        when(repository.count()).thenReturn(0L);

        initializer("", "").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void refuses_to_start_with_a_weak_password() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer("maxime", "court").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGORAPILOT_ADMIN_PASSWORD");
        verify(repository, never()).save(any());
    }

    @Test
    void refuses_to_start_with_an_invalid_username() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer("maxime chazard", "un mot de passe provisoire").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGORAPILOT_ADMIN_USERNAME");
    }
}
