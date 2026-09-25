package fr.maximechazard.agorapilot.back.publication;

/**
 * Cycle de vie d'une livraison sur un canal.
 * <p>
 * {@code IN_PROGRESS} est posé et validé en base <em>avant</em> l'appel au canal
 * distant. Si le back s'arrête pendant la diffusion, la livraison n'est donc
 * jamais retrouvée {@code PENDING} — ce qui la ferait republier au balayage
 * suivant — mais {@code IN_PROGRESS}, que l'ordonnanceur solde en
 * {@code FAILED} : le canal a peut-être accepté la publication, c'est à un
 * humain de vérifier avant de relancer.
 */
public enum DeliveryStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED
}
