package fr.maximechazard.agorapilot.back.account.admin;

import fr.maximechazard.agorapilot.back.account.AccountStatus;
import fr.maximechazard.agorapilot.back.account.AuthService;
import fr.maximechazard.agorapilot.back.account.Permission;
import fr.maximechazard.agorapilot.back.account.UserAccount;
import fr.maximechazard.agorapilot.back.account.UserAccountRepository;
import fr.maximechazard.agorapilot.back.account.Usernames;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import fr.maximechazard.agorapilot.back.account.events.SecurityEventType;
import fr.maximechazard.agorapilot.back.account.exceptions.AccountNotFoundException;
import fr.maximechazard.agorapilot.back.account.exceptions.ForbiddenAccountOperationException;
import fr.maximechazard.agorapilot.back.account.exceptions.InvalidAccountRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administration des comptes, réservée au droit {@code users:manage}.
 * <p>
 * Deux garde-fous, en plus de ce droit :
 * <ul>
 *     <li><strong>jamais sur son propre compte</strong> : ni ses droits, ni sa
 *     désactivation, ni son mot de passe (qui se change par {@code /api/me}).
 *     L'administrateur qui agit reste donc actif avec {@code users:manage} : il
 *     reste toujours au moins un administrateur ;</li>
 *     <li><strong>seulement les droits qu'on détient</strong>, pour les donner
 *     comme pour les retirer.</li>
 * </ul>
 * Toute action est inscrite au journal de sécurité.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAdminService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final SecurityEventService events;

    @Transactional(readOnly = true)
    public List<AccountDTO> list() {
        LocalDateTime now = authService.now();

        return repository.findAll().stream()
                .sorted(Comparator.comparing(UserAccount::getUsername))
                .map(account -> toDTO(account, now))
                .toList();
    }

    /**
     * Crée un compte au mot de passe provisoire, à changer à la première
     * connexion.
     *
     * @throws InvalidAccountRequestException pseudo hors règle ou déjà pris, droit inconnu
     * @throws ForbiddenAccountOperationException un droit que l'administrateur ne détient pas
     */
    @Transactional
    public TemporaryPasswordDTO create(Long actorId, CreateAccountRequest request) {
        UserAccount actor = actor(actorId);
        String username = Usernames.normalize(request.getUsername());

        if (!Usernames.isValid(username)) {
            throw new InvalidAccountRequestException(
                    "Pseudo invalide : 3 à 32 caractères parmi a-z, 0-9, « . », « _ » et « - ».", false);
        }
        if (repository.findByUsername(username).isPresent()) {
            throw new InvalidAccountRequestException("Le pseudo « " + username + " » est déjà pris.", true);
        }

        Set<Permission> permissions = parse(request.getPermissions());
        requireHeld(actor, permissions);

        String temporaryPassword = TemporaryPasswords.generate();
        UserAccount account = repository.save(new UserAccount(username, passwordEncoder.encode(temporaryPassword),
                AccountStatus.PENDING_PASSWORD, permissions, authService.now()));

        log.info("Compte {} créé par {}", username, actor.getUsername());
        events.record(SecurityEventType.ACCOUNT_CREATED, actor.getUsername(), username, describe(permissions));

        return new TemporaryPasswordDTO(toDTO(account, authService.now()), temporaryPassword);
    }

    /**
     * Remplace les droits d'un compte. Pris en compte dès sa requête suivante.
     *
     * @throws ForbiddenAccountOperationException son propre compte, ou un droit ajouté ou retiré qu'on ne détient pas
     */
    @Transactional
    public AccountDTO setPermissions(Long actorId, Long targetId, SetPermissionsRequest request) {
        UserAccount actor = actor(actorId);
        UserAccount target = target(actor, targetId, "modifier vos propres droits");
        Set<Permission> requested = parse(request.getPermissions());

        Set<Permission> added = EnumSet.noneOf(Permission.class);
        added.addAll(requested);
        added.removeAll(target.getPermissions());
        Set<Permission> removed = EnumSet.noneOf(Permission.class);
        removed.addAll(target.getPermissions());
        removed.removeAll(requested);

        Set<Permission> changed = EnumSet.noneOf(Permission.class);
        changed.addAll(added);
        changed.addAll(removed);
        requireHeld(actor, changed);

        if (changed.isEmpty()) {
            return toDTO(target, authService.now());
        }

        target.getPermissions().clear();
        target.getPermissions().addAll(requested);
        repository.save(target);

        log.info("Droits de {} modifiés par {}", target.getUsername(), actor.getUsername());
        events.record(SecurityEventType.PERMISSIONS_CHANGED, actor.getUsername(), target.getUsername(),
                "Ajoutés : " + describe(added) + " ; retirés : " + describe(removed));

        return toDTO(target, authService.now());
    }

    /**
     * Désactive un compte : connexion refusée, sessions coupées à leur requête
     * suivante. Le compte n'est jamais supprimé ; le réactiver passe par une
     * réinitialisation de son mot de passe.
     */
    @Transactional
    public AccountDTO disable(Long actorId, Long targetId) {
        UserAccount actor = actor(actorId);
        UserAccount target = target(actor, targetId, "désactiver votre propre compte");

        if (target.getStatus() != AccountStatus.DISABLED) {
            target.setStatus(AccountStatus.DISABLED);
            repository.save(target);
            log.info("Compte {} désactivé par {}", target.getUsername(), actor.getUsername());
            events.record(SecurityEventType.ACCOUNT_DISABLED, actor.getUsername(), target.getUsername(), null);
        }

        return toDTO(target, authService.now());
    }

    /**
     * Donne un nouveau mot de passe provisoire : le compte repasse en attente de
     * changement, ses sessions sont coupées, son verrouillage levé. C'est aussi
     * ainsi qu'on réactive un compte désactivé — jamais avec son ancien mot de
     * passe.
     */
    @Transactional
    public TemporaryPasswordDTO resetPassword(Long actorId, Long targetId) {
        UserAccount actor = actor(actorId);
        UserAccount target = target(actor, targetId, "réinitialiser votre propre mot de passe (changez-le depuis votre compte)");
        AccountStatus previous = target.getStatus();

        String temporaryPassword = TemporaryPasswords.generate();
        target.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        target.setPasswordChangedAt(authService.now());
        target.setStatus(AccountStatus.PENDING_PASSWORD);
        target.setFailedAttempts(0);
        target.setLockedUntil(null);
        repository.save(target);

        log.info("Mot de passe de {} réinitialisé par {}", target.getUsername(), actor.getUsername());
        events.record(SecurityEventType.PASSWORD_RESET, actor.getUsername(), target.getUsername(),
                previous == AccountStatus.DISABLED ? "Compte réactivé" : null);

        return new TemporaryPasswordDTO(toDTO(target, authService.now()), temporaryPassword);
    }

    // ------------------------------- Interne -------------------------------

    /**
     * L'administrateur qui agit, relu ici aussi : le droit {@code users:manage}
     * est déjà vérifié par la chaîne de sécurité, mais ce service ne compte pas
     * sur elle seule.
     */
    private UserAccount actor(Long actorId) {
        UserAccount actor = repository.findById(actorId).orElseThrow(() -> new AccountNotFoundException(actorId));

        if (!actor.effectivePermissions().contains(Permission.USERS_MANAGE)) {
            throw new ForbiddenAccountOperationException("Vous n'avez pas le droit d'administrer les comptes.");
        }

        return actor;
    }

    private UserAccount target(UserAccount actor, Long targetId, String forbiddenOnSelf) {
        if (actor.getId().equals(targetId)) {
            throw new ForbiddenAccountOperationException("Vous ne pouvez pas " + forbiddenOnSelf + ".");
        }

        return repository.findById(targetId).orElseThrow(() -> new AccountNotFoundException(targetId));
    }

    private static void requireHeld(UserAccount actor, Set<Permission> permissions) {
        Set<Permission> missing = EnumSet.noneOf(Permission.class);
        missing.addAll(permissions);
        missing.removeAll(actor.effectivePermissions());

        if (!missing.isEmpty()) {
            throw new ForbiddenAccountOperationException(
                    "Vous ne pouvez donner ou retirer que des droits que vous détenez vous-même : " + describe(missing) + ".");
        }
    }

    private static Set<Permission> parse(List<String> codes) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);

        for (String code : codes) {
            permissions.add(Permission.fromCode(code)
                    .orElseThrow(() -> new InvalidAccountRequestException("Droit inconnu : « " + code + " ».", false)));
        }

        return permissions;
    }

    private static String describe(Set<Permission> permissions) {
        return permissions.isEmpty() ? "aucun" : permissions.stream()
                .sorted()
                .map(Permission::code)
                .collect(Collectors.joining(", "));
    }

    private static AccountDTO toDTO(UserAccount account, LocalDateTime now) {
        return new AccountDTO(account.getId(), account.getUsername(), account.getStatus(),
                account.getPermissions().stream().sorted().map(Permission::code).toList(),
                account.isLocked(now) ? account.getLockedUntil() : null,
                account.getLastLoginAt(), account.getCreatedAt());
    }
}
