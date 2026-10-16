package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sous {@code /api} et non {@code /admin} : le bandeau du front affiche la
 * suspension et propose de la lever, il doit donc pouvoir joindre ces endpoints.
 */
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelSuspensionController {

    private final ChannelSuspensionService channelSuspensionService;

    /** Les canaux suspendus ; liste vide quand tout fonctionne. */
    @GetMapping("/suspensions")
    public ResponseEntity<List<ChannelSuspensionDTO>> getSuspensions() {
        return ResponseEntity.ok(channelSuspensionService.findAll());
    }

    /** Lève la suspension du canal ; idempotent. */
    @DeleteMapping("/{channel}/suspension")
    public ResponseEntity<Void> lift(@PathVariable DeliveryChannel channel) {
        channelSuspensionService.lift(channel);
        return ResponseEntity.noContent().build();
    }
}
