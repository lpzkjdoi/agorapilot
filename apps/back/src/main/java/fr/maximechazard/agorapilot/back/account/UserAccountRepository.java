package fr.maximechazard.agorapilot.back.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    /** Le pseudo doit déjà être en minuscules (cf. {@link Usernames#normalize}). */
    Optional<UserAccount> findByUsername(String username);
}
