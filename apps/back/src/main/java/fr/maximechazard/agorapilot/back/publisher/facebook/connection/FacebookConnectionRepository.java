package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacebookConnectionRepository extends JpaRepository<FacebookConnection, Long> {
    Optional<FacebookConnection> findTopByOrderByConnectedAtDesc();
}
