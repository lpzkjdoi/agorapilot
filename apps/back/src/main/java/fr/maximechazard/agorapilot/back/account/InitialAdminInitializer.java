package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.exceptions.InvalidPasswordException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Crée le premier compte, administrateur, si la base n'en a aucun : à partir de
 * {@code AGORAPILOT_ADMIN_USERNAME} et {@code AGORAPILOT_ADMIN_PASSWORD}.
 * <p>
 * Ce mot de passe est provisoire, comme tout mot de passe donné par un autre que
 * soi : il faut le changer à la première connexion. Une valeur hors règle arrête
 * le démarrage plutôt que de créer un compte faible. Sans ces variables,
 * l'application démarre sans compte, et personne ne peut s'y connecter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InitialAdminInitializer implements ApplicationRunner {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final AuthService authService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        SecurityProperties.InitialAdmin admin = properties.initialAdmin();

        if (admin == null || !admin.isConfigured()) {
            log.warn("Aucun compte en base, et AGORAPILOT_ADMIN_USERNAME / AGORAPILOT_ADMIN_PASSWORD ne sont pas "
                    + "renseignés : personne ne peut se connecter.");
            return;
        }

        String username = Usernames.normalize(admin.username());

        if (!Usernames.isValid(username)) {
            throw new IllegalStateException("AGORAPILOT_ADMIN_USERNAME invalide : 3 à 32 caractères parmi a-z, 0-9, '.', '_' et '-'.");
        }

        try {
            PasswordPolicy.check(admin.password(), username);
        } catch (InvalidPasswordException e) {
            throw new IllegalStateException("AGORAPILOT_ADMIN_PASSWORD refusé : " + e.getMessage(), e);
        }

        repository.save(new UserAccount(username, passwordEncoder.encode(admin.password()),
                AccountStatus.PENDING_PASSWORD, EnumSet.allOf(Permission.class), authService.now()));
        log.info("Premier compte créé : {}, administrateur, mot de passe provisoire à changer à la première connexion.", username);
    }
}
