package fr.maximechazard.agorapilot.back.account.events;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SecurityEventService {
    public static final int MAX_LIMIT = 200;

    private final SecurityEventRepository repository;
    private final Clock clock;

    /** Écrit dans la transaction de l'appelant : une action annulée ne laisse pas de trace trompeuse. */
    @Transactional
    public void record(SecurityEventType type, String actor, String target, String details) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        repository.save(new SecurityEvent(now, type, actor, target, details));
    }

    /** Les événements les plus récents d'abord, {@value #MAX_LIMIT} au plus. */
    @Transactional(readOnly = true)
    public List<SecurityEventDTO> latest(int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        return repository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, size)).stream()
                .map(SecurityEventDTO::of)
                .toList();
    }
}
