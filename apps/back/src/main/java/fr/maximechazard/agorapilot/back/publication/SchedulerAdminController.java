package fr.maximechazard.agorapilot.back.publication;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * État de l'ordonnanceur de diffusion, pour savoir s'il tourne et ce qui l'a
 * fait échouer en dernier sans fouiller les logs du conteneur.
 * <p>
 * Comme {@code /admin/facebook}, non authentifié et non proxifié par le nginx du
 * front : joignable uniquement sur la boucle locale du VPS.
 */
@RestController
@RequestMapping("/admin/scheduler")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final PublicationOccurrenceScheduler scheduler;
    private final SchedulingProperties schedulingProperties;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lastRunAt", scheduler.getLastRunAt());
        body.put("lastErrorAt", scheduler.getLastErrorAt());
        body.put("lastError", scheduler.getLastError());
        body.put("occurrencesDelay", schedulingProperties.occurrencesDelay().toString());
        body.put("maxLateness", schedulingProperties.maxLateness().toString());
        body.put("windowStart", schedulingProperties.windowStart().toString());
        body.put("windowEnd", schedulingProperties.windowEnd().toString());
        return ResponseEntity.ok(body);
    }
}
