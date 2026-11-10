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
  /** `null` tant qu'aucune Page n'a été connectée : rien ne peut alors être publié. */
  connection: FacebookConnection | null
}

export const CHECK_RESULT_LABELS: Record<FacebookCheckResult, string> = {
  VALID: 'Valide',
  INVALID: 'Invalide',
  UNKNOWN: 'Indéterminé',
};
