package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Suspension des canaux dont le compte est bloqué.
 * <p>
 * Quand Facebook bloque la Page (code 368) ou invalide le jeton (code 190),
 * chaque diffusion suivante échouerait de la même façon. Pire : insister sur un
 * compte bloqué peut prolonger le blocage. Le publisher suspend donc le canal au
 * premier refus de ce type ; l'ordonnanceur et « Publier maintenant » consultent
 * la suspension avant tout appel.
 * <p>
 * La levée est toujours un geste explicite : rien côté AgoraPilot ne sait dire
 * si l'identité a été confirmée sur Facebook ou si un nouveau jeton est en place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelSuspensionService {

    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final ChannelSuspensionRepository channelSuspensionRepository;
    private final Clock clock;

    public Optional<ChannelSuspension> find(DeliveryChannel channel) {
        return channelSuspensionRepository.findById(channel);
    }

    public List<ChannelSuspensionDTO> findAll() {
        return channelSuspensionRepository.findAll().stream().map(ChannelSuspensionDTO::of).toList();
    }

    /**
     * Suspend le canal. Une suspension déjà en place est conservée telle quelle :
     * c'est le premier refus qui explique le blocage, et sa date qui dit depuis
     * quand les diffusions attendent.
     */
    @Transactional
    public void suspend(DeliveryChannel channel, String reason) {
        if (channelSuspensionRepository.existsById(channel)) {
            return;
        }

        log.warn("Canal {} suspendu : {}", channel, reason);
        channelSuspensionRepository.save(new ChannelSuspension(channel, reason, LocalDateTime.now(clock)));
    }

    /** Lève la suspension ; sans effet si le canal n'est pas suspendu. */
    @Transactional
    public void lift(DeliveryChannel channel) {
        channelSuspensionRepository.findById(channel).ifPresent(suspension -> {
            log.info("Suspension du canal {} levée (en place depuis le {}).",
                    channel, suspension.getSuspendedAt().format(SINCE));
            channelSuspensionRepository.delete(suspension);
        });
    }

    /**
     * Refuse une diffusion immédiate sur un canal suspendu.
     *
     * @throws ChannelSuspendedException le canal est suspendu
     */
    public void ensureNotSuspended(DeliveryChannel channel) {
        find(channel).ifPresent(suspension -> {
            throw new ChannelSuspendedException(describe(suspension)
                    + " Levez la suspension une fois le problème réglé sur " + label(channel) + ".");
        });
    }

    /**
     * {@code Diffusions Facebook suspendues depuis le 16/10/2026 à 14:05 : Confirmez votre identité… (Facebook, code 368).}
     */
    public static String describe(ChannelSuspension suspension) {
        return "Diffusions " + label(suspension.getChannel()) + " suspendues depuis le "
                + suspension.getSuspendedAt().format(SINCE) + " : " + suspension.getReason();
    }

    private static String label(DeliveryChannel channel) {
        return switch (channel) {
            case FACEBOOK -> "Facebook";
            case INTRAMUROS -> "Intramuros";
        };
    }
}
