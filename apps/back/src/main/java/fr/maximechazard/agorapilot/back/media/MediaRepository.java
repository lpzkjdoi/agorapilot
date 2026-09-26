package fr.maximechazard.agorapilot.back.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findAllByArchivedOrderByCreatedAtDesc(Boolean archived);

    List<Media> findAllByOrderByCreatedAtDesc();
}
