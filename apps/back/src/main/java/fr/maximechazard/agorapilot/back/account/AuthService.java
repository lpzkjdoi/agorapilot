package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.exceptions.AuthenticationFailedException;
import fr.maximechazard.agorapilot.back.account.exceptions.InvalidPasswordException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Slf4j
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    /**
     * Empreinte comparée quand le pseudo est inconnu : la réponse prend alors le
     * même temps qu'avec un vrai compte, et ne trahit pas son absence.
     */
    private final String dummyHash;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                       SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("agorapilot-dummy-password");
    }

    /**
     * Vérifie pseudo et mot de passe.
     * <p>
     * Un compte verrouillé n'est pas même testé : un mot de passe juste pendant
     * le verrouillage ne doit rien apprendre à qui essaie. Au bout de
     * {@code maxFailedAttempts} échecs consécutifs, le compte est verrouillé pour
     * {@code lockDuration}.
     *
     * @throws AuthenticationFailedException quelle qu'en soit la cause
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public UserAccount authenticate(String rawUsername, String password) {
        String username = Usernames.normalize(rawUsername);
        Optional<UserAccount> found = repository.findByUsername(username);

        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            log.info("Connexion refusée : pseudo inconnu");
            throw new AuthenticationFailedException();
        }

        UserAccount account = found.get();
        LocalDateTime now = now();

        if (account.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            log.info("Connexion refusée pour {} : compte verrouillé jusqu'à {}", account.getUsername(), account.getLockedUntil());
            throw new AuthenticationFailedException();
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            recordFailure(account, now);
            throw new AuthenticationFailedException();
        }

        if (account.getStatus() == AccountStatus.DISABLED) {
            log.info("Connexion refusée pour {} : compte désactivé", account.getUsername());
            throw new AuthenticationFailedException();
        }

        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(now);
        log.info("Connexion de {}", account.getUsername());

        return repository.save(account);
    }

    /**
     * Change le mot de passe du compte connecté ; un mot de passe provisoire
     * devient définitif et le compte exerce enfin ses droits. Les autres sessions
     * du compte sont coupées (cf. {@link UserAccount#getPasswordChangedAt()}).
     *
     * @return l'heure du changement, qui devient celle de la session courante
     * @throws InvalidPasswordException mot de passe actuel erroné, ou nouveau hors règle
     */
    @Transactional
    public LocalDateTime changePassword(Long accountId, String currentPassword, String newPassword) {
        UserAccount account = repository.findById(accountId)
                .orElseThrow(AuthenticationFailedException::new);

        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new InvalidPasswordException("Le mot de passe actuel est incorrect.");
        }

        PasswordPolicy.check(newPassword, account.getUsername());

        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new InvalidPasswordException("Le nouveau mot de passe doit être différent de l'actuel.");
        }

        LocalDateTime now = now();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordChangedAt(now);

        if (account.getStatus() == AccountStatus.PENDING_PASSWORD) {
            account.setStatus(AccountStatus.ACTIVE);
        }

        repository.save(account);
        log.info("Mot de passe changé par {}", account.getUsername());

        return now;
    }

    /**
     * Le compte d'une session, s'il peut encore s'en servir : il existe, n'est pas
     * désactivé, n'a pas changé de mot de passe depuis la connexion, et la session
     * n'a pas dépassé sa durée de vie maximale.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> stillValid(AuthenticatedUser user) {
        LocalDateTime now = now();

        if (user.authenticatedAt().plus(properties.sessionMaxAge()).isBefore(now)) {
            return Optional.empty();
        }

        return repository.findById(user.id())
                .filter(account -> account.getStatus() != AccountStatus.DISABLED)
                .filter(account -> !account.getPasswordChangedAt().isAfter(user.authenticatedAt()));
    }

    /** Précision de la base : une heure relue doit être égale à celle écrite. */
    public LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private void recordFailure(UserAccount account, LocalDateTime now) {
        int failures = account.getFailedAttempts() + 1;

        if (failures >= properties.maxFailedAttempts()) {
            account.setFailedAttempts(0);
            account.setLockedUntil(now.plus(properties.lockDuration()));
            log.warn("Compte {} verrouillé jusqu'à {} après {} échecs de connexion",
                    account.getUsername(), account.getLockedUntil(), failures);
        } else {
            account.setFailedAttempts(failures);
            log.info("Connexion refusée pour {} : mot de passe erroné ({} échec(s))", account.getUsername(), failures);
        }

        repository.save(account);
    }
}
