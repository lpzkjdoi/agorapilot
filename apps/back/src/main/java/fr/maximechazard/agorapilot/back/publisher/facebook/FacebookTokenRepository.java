package fr.maximechazard.agorapilot.back.publisher.facebook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacebookTokenRepository extends JpaRepository<FacebookToken, Long> {
    Optional<FacebookToken> findTopByTokenTypeOrderByUpdatedAtDesc(String tokenType);
}