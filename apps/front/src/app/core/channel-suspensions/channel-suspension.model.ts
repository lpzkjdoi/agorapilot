import { DeliveryChannel } from '../../features/publications/publication.model';

/**
 * Canal coupé après un refus qui bloque le compte (Facebook : Page bloquée,
 * jeton invalide), tel qu'exposé par `GET /api/channels/suspensions`.
 */
export interface ChannelSuspension {
  channel: DeliveryChannel;
  /** Le refus qui a déclenché la suspension, déjà formulé pour l'utilisateur. */
  reason: string;
  /** Heure locale du serveur, sans fuseau (`2026-10-16T14:05:00`). */
  suspendedAt: string;
}

/** Libellé affiché d'un canal. */
export const CHANNEL_LABELS: Record<DeliveryChannel, string> = {
  FACEBOOK: 'Facebook',
  INTRAMUROS: 'Intramuros',
};
