/** `FACEBOOK_TOKEN_SOURCE` : `LEGACY` est le retour arrière vers le bootstrap à la main. */
export type FacebookTokenSource = 'CONNECTION' | 'LEGACY';

/** D'où vient le jeton qui publie réellement. */
export type FacebookTokenOrigin = 'CONNECTION' | 'LEGACY' | 'NONE';

export type FacebookCheckResult = 'VALID' | 'INVALID' | 'UNKNOWN';

/** Reflète `FacebookConnectionStatusDTO.Connection` ; dates en heure locale du serveur, sans fuseau. */
export interface FacebookConnection {
  pageId: string
  pageName: string
  scopes: string[]
  connectedBy: string
  connectedAt: string
  /** `null` : jeton permanent. */
  expiresAt: string | null
  dataAccessExpiresAt: string | null
  lastCheckedAt: string | null
  lastCheckResult: FacebookCheckResult | null
  lastCheckMessage: string | null
}

/** Reflète `FacebookConnectionStatusDTO` (`GET /api/channels/facebook/connection`). */
export interface FacebookConnectionStatus {
  source: FacebookTokenSource
  tokenInUse: FacebookTokenOrigin
  /** `null` tant qu'aucune Page n'a été connectée depuis AgoraPilot. */
  connection: FacebookConnection | null
}

export const TOKEN_ORIGIN_LABELS: Record<FacebookTokenOrigin, string> = {
  CONNECTION: 'Jeton connecté depuis AgoraPilot',
  LEGACY: 'Jeton historique (bootstrap à la main)',
  NONE: 'Aucun jeton : rien ne peut être publié',
};

export const CHECK_RESULT_LABELS: Record<FacebookCheckResult, string> = {
  VALID: 'Valide',
  INVALID: 'Invalide',
  UNKNOWN: 'Indéterminé',
};
