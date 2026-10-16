import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

export type NotificationLevel = 'error' | 'success' | 'info';

export interface AppNotification {
  id: number;
  level: NotificationLevel;
  message: string;
}

const AUTO_DISMISS_MS = 5000;

@Injectable({
  providedIn: 'root',
})
export class NotificationsService {
  private nextId = 0;
  private readonly _notifications = signal<AppNotification[]>([]);

  readonly notifications = this._notifications.asReadonly();

  /**
   * Affiche une notification. Accepte un message ou une erreur quelconque
   * (HttpErrorResponse, Error, ...) qui est convertie en message lisible ;
   * une erreur sans niveau explicite est affichée en niveau `error`.
   */
  send(content: unknown, level?: NotificationLevel) {
    const notification: AppNotification = {
      id: this.nextId++,
      level: level ?? (typeof content === 'string' ? 'info' : 'error'),
      message: this.toMessage(content),
    };

    this._notifications.update((list) => [...list, notification]);
    setTimeout(() => this.dismiss(notification.id), AUTO_DISMISS_MS);
  }

  dismiss(id: number) {
    this._notifications.update((list) => list.filter((n) => n.id !== id));
  }

  private toMessage(content: unknown): string {
    if (typeof content === 'string') {
      return content;
    }
    if (content instanceof HttpErrorResponse) {
      if (content.status === 0) {
        return 'Impossible de joindre le serveur.';
      }
      const apiMessage = (content.error as { message?: unknown } | null)?.message;
      return typeof apiMessage === 'string' && apiMessage
        ? apiMessage
        : `Erreur serveur (${content.status}).`;
    }
    if (content instanceof Error && content.message) {
      return content.message;
    }
    return 'Une erreur inattendue est survenue.';
  }
}
