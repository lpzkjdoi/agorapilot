import { AccountStatus, Permission } from "../../core/auth/auth.model";

/** Reflète `AccountDTO` (`GET /api/users`). */
export interface Account {
  id: number
  username: string
  status: AccountStatus
  /** Droits attribués, même s'ils ne sont pas encore exercés (mot de passe provisoire). */
  permissions: Permission[]
  /** Fin du verrouillage en cours, `null` s'il n'y en a pas. */
  lockedUntil: string | null
  lastLoginAt: string | null
  createdAt: string | null
}

/**
 * Réponse d'une création ou d'une réinitialisation. Le mot de passe provisoire
 * n'est donné qu'une fois : le back n'en garde que l'empreinte.
 */
export interface TemporaryPassword {
  account: Account
  temporaryPassword: string
}

export interface CreateAccountRequest {
  username: string
  permissions: Permission[]
}

export type SecurityEventType =
  | 'LOGIN_SUCCEEDED'
  | 'LOGIN_FAILED'
  | 'ACCOUNT_LOCKED'
  | 'PASSWORD_CHANGED'
  | 'ACCOUNT_CREATED'
  | 'PERMISSIONS_CHANGED'
  | 'ACCOUNT_DISABLED'
  | 'PASSWORD_RESET'
  | 'PUBLICATION_DELETED'
  | 'OCCURRENCE_RESCHEDULED'
  | 'OCCURRENCE_RETRIED'
  | 'OCCURRENCE_CANCELLED'
  | 'CHANNEL_SUSPENDED'
  | 'CHANNEL_RESUMED'
  | 'FACEBOOK_CONNECTED';

/** Reflète `SecurityEventDTO` (`GET /api/security-events`). */
export interface SecurityEvent {
  id: number
  occurredAt: string
  type: SecurityEventType
  actor: string | null
  target: string | null
  details: string | null
}

export const STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING_PASSWORD: 'Mot de passe provisoire',
  ACTIVE: 'Actif',
  DISABLED: 'Désactivé',
};

export const EVENT_LABELS: Record<SecurityEventType, string> = {
  LOGIN_SUCCEEDED: 'Connexion',
  LOGIN_FAILED: 'Connexion refusée',
  ACCOUNT_LOCKED: 'Compte verrouillé',
  PASSWORD_CHANGED: 'Mot de passe changé',
  ACCOUNT_CREATED: 'Compte créé',
  PERMISSIONS_CHANGED: 'Droits modifiés',
  ACCOUNT_DISABLED: 'Compte désactivé',
  PASSWORD_RESET: 'Mot de passe réinitialisé',
  PUBLICATION_DELETED: 'Publication supprimée',
  OCCURRENCE_RESCHEDULED: 'Diffusion déplacée',
  OCCURRENCE_RETRIED: 'Diffusion reprise',
  OCCURRENCE_CANCELLED: 'Diffusion annulée',
  CHANNEL_SUSPENDED: 'Canal suspendu',
  CHANNEL_RESUMED: 'Canal repris',
  FACEBOOK_CONNECTED: 'Page Facebook connectée',
};
