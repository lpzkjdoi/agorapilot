package fr.maximechazard.agorapilot.back.repository;

import fr.maximechazard.agorapilot.back.publishing.channel.facebook.FacebookToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacebookTokenRepository extends JpaRepository<FacebookToken, Long> {
    Optional<FacebookToken> findTopByTokenTypeOrderByUpdatedAtDesc(String tokenType);
}