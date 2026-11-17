package fr.maximechazard.agorapilot.back.publication.recurrence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job quotidien des séries : chaque nuit, il prolonge les séries actives jusqu'à
 * l'horizon. Une nuit manquée (back arrêté) n'est pas grave : l'horizon couvre
 * largement les jours suivants, et la création d'une série produit déjà ses
 * premières diffusions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OccurrenceSeriesScheduler {

    private final OccurrenceSeriesService seriesService;

    @Scheduled(cron = "${agorapilot.recurrence.cron:0 5 0 * * *}")
    public void extendSeries() {
        int created = seriesService.generateAll();

        if (created > 0) {
            log.info("{} diffusion(s) créée(s) par les séries.", created);
        }
    }
}
