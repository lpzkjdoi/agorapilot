import { computed, Injectable, OnDestroy, signal } from '@angular/core';
import {
  AppNotification,
  DEFAULT_NOTIFICATION_DURATION_MS,
  MAX_STACKED_NOTIFICATIONS,
  NOTIFICATION_EXIT_DURATION_MS,
  NotificationInput,
} from './notification.model';

/**
 * Pile de notifications de l'application.
 *
 * Service global (`providedIn: 'root'`) : n'importe quel composant ou service
 * peut l'injecter et empiler un message sans se soucier de l'affichage, rendu
 * par le portal (voir `NotificationPortalService`).
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService implements OnDestroy {
  private readonly stack = signal<readonly AppNotification[]>([]);

  /** La pile, du plus ancien au plus récent — l'ordre d'affichage du portal. */
  readonly notifications = this.stack.asReadonly();

  /** Nombre de notifications encore visibles (hors animation de sortie). */
  readonly count = computed(
    () => this.stack().filter((notification) => !notification.closing).length,
  );

  private nextId = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  /**
   * Empile une notification et renvoie son identifiant, utilisable ensuite avec
   * `closeNotification()` pour la fermer avant la fin de sa durée d'affichage.
   */
  sendNotification(input: NotificationInput): number {
    const notification: AppNotification = {
      id: ++this.nextId,
      level: input.level,
      message: input.message,
      title: input.title,
      duration: input.duration ?? DEFAULT_NOTIFICATION_DURATION_MS,
      dismissible: input.dismissible ?? true,
      closing: false,
    };

    this.stack.update((stack) => [...stack, notification]);
    this.closeOverflow();

    if (notification.duration > 0) {
      this.schedule(notification.id, notification.duration, () =>
        this.closeNotification(notification.id),
      );
    }

    return notification.id;
  }

  /**
   * Ferme une notification. Elle reste dans la pile le temps de l'animation de
   * sortie, marquée `closing`, puis en est retirée.
   */
  closeNotification(id: number): void {
    const target = this.stack().find((notification) => notification.id === id);
    if (!target || target.closing) {
      return;
    }

    this.stack.update((stack) =>
      stack.map((notification) =>
        notification.id === id ? { ...notification, closing: true } : notification,
      ),
    );

    this.schedule(id, NOTIFICATION_EXIT_DURATION_MS, () => this.remove(id));
  }

  /** Ferme toutes les notifications, par exemple lors d'un changement de page. */
  closeAll(): void {
    this.stack().forEach((notification) => this.closeNotification(notification.id));
  }

  success(message: string, title?: string): number {
    return this.sendNotification({ level: 'success', message, title });
  }

  info(message: string, title?: string): number {
    return this.sendNotification({ level: 'info', message, title });
  }

  warning(message: string, title?: string): number {
    return this.sendNotification({ level: 'warning', message, title });
  }

  error(message: string, title?: string): number {
    return this.sendNotification({ level: 'error', message, title });
  }

  ngOnDestroy(): void {
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
  }

  /** Une seule minuterie par notification : l'auto-fermeture puis le retrait. */
  private schedule(id: number, delay: number, action: () => void): void {
    const pending = this.timers.get(id);
    if (pending !== undefined) {
      clearTimeout(pending);
    }

    this.timers.set(
      id,
      setTimeout(() => {
        this.timers.delete(id);
        action();
      }, delay),
    );
  }

  private remove(id: number): void {
    this.stack.update((stack) => stack.filter((notification) => notification.id !== id));
  }

  private closeOverflow(): void {
    const visible = this.stack().filter((notification) => !notification.closing);
    visible
      .slice(0, Math.max(0, visible.length - MAX_STACKED_NOTIFICATIONS))
      .forEach((notification) => this.closeNotification(notification.id));
  }
}
