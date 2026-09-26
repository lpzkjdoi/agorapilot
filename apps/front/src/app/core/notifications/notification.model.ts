/** Niveau d'une notification : pilote l'icône, la couleur et le rôle ARIA. */
export type NotificationLevel = 'success' | 'info' | 'warning' | 'error';

/** Ce que l'appelant passe à `NotificationService.sendNotification()`. */
export interface NotificationInput {
  level: NotificationLevel;
  message: string;
  /** Titre facultatif, affiché en gras au-dessus du message. */
  title?: string;
  /** Durée d'affichage en ms. `0` garde la notification jusqu'à fermeture manuelle. */
  duration?: number;
  /** Passer `false` retire la croix de fermeture. */
  dismissible?: boolean;
}

/**
 * Une notification empilée dans le portal.
 *
 * Nommée `AppNotification` et non `Notification` : ce dernier est déjà le type
 * global de l'API Web Notifications, et le masquer dans chaque fichier qui
 * importe le modèle rend les erreurs de typage illisibles.
 */
export interface AppNotification {
  readonly id: number;
  readonly level: NotificationLevel;
  readonly message: string;
  readonly title?: string;
  readonly duration: number;
  readonly dismissible: boolean;
  /** `true` pendant l'animation de sortie, avant le retrait effectif de la pile. */
  readonly closing: boolean;
}

/** Durée d'affichage par défaut, alignée sur la maquette. */
export const DEFAULT_NOTIFICATION_DURATION_MS = 5_000;

/** Doit rester synchronisée avec l'animation `notification-leave` du toast. */
export const NOTIFICATION_EXIT_DURATION_MS = 220;

/** Au-delà, les plus anciennes se ferment — le `visibleToasts` de sonner. */
export const MAX_STACKED_NOTIFICATIONS = 3;

/** Écart entre deux notifications dépliées, et décalage du dépassement replié. */
export const NOTIFICATION_STACK_GAP_PX = 14;

/** Réduction appliquée à chaque cran de profondeur quand la pile est repliée. */
export const NOTIFICATION_STACK_SCALE_STEP = 0.05;

/** Durée du repli / dépliage de la pile. */
export const NOTIFICATION_STACK_TRANSITION_MS = 400;
