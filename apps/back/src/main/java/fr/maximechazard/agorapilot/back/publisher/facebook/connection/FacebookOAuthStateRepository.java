package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface FacebookOAuthStateRepository extends JpaRepository<FacebookOAuthState, String> {

    @Modifying
    @Query("DELETE FROM FacebookOAuthState s WHERE s.createdAt < :before")
    void deleteCreatedBefore(LocalDateTime before);
}
