/** Droits d'un compte (`Permission` côté back), notés `ressource:action`. */
export type Permission =
  | 'publications:read'
  | 'publications:write'
  | 'publications:verify'
  | 'publications:delete'
  | 'publications:publish'
  | 'occurrences:schedule'
  | 'campaigns:read'
  | 'campaigns:write'
  | 'medias:read'
  | 'medias:write'
  | 'medias:delete'
  | 'channels:manage'
  | 'users:manage';

/**
 * `PENDING_PASSWORD` : mot de passe provisoire, à changer avant toute autre
 * action ; le compte n'exerce aucun droit d'ici là.
 */
export type AccountStatus = 'PENDING_PASSWORD' | 'ACTIVE' | 'DISABLED';

/** Reflète `CurrentUserDTO` (`GET /api/me`, `POST /api/auth/login`). */
export interface CurrentUser {
  username: string
  status: AccountStatus
  /** Droits exercés ; vide tant que le mot de passe est provisoire. */
  permissions: Permission[]
}

export interface LoginRequest {
  username: string
  password: string
}

/** Corps de `PUT /api/me/password`. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** Règle du back (`PasswordPolicy`), reprise pour guider la saisie. */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;
