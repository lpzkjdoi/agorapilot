import { Permission } from "../../core/auth/auth.model";

export interface PermissionGroup {
  label: string
  permissions: { code: Permission, label: string }[]
}

/** Les droits, regroupés et décrits pour l'administrateur qui les coche. */
export const PERMISSION_GROUPS: PermissionGroup[] = [
  {
    label: 'Publications et diffusion',
    permissions: [
      { code: 'publications:read', label: 'Voir les publications, le calendrier et le tableau de bord' },
      { code: 'publications:write', label: 'Créer et modifier les publications' },
      { code: 'publications:verify', label: 'Vérifier les publications' },
      { code: 'publications:delete', label: 'Supprimer les publications' },
      { code: 'occurrences:schedule', label: 'Programmer, déplacer et reprendre les diffusions' },
      { code: 'publications:publish', label: 'Diffuser immédiatement' },
    ],
  },
  {
    label: 'Campagnes',
    permissions: [
      { code: 'campaigns:read', label: 'Voir les campagnes' },
      { code: 'campaigns:write', label: 'Créer et clôturer les campagnes' },
    ],
  },
  {
    label: 'Médiathèque',
    permissions: [
      { code: 'medias:read', label: 'Voir la médiathèque' },
      { code: 'medias:write', label: 'Ajouter, renommer et archiver des médias' },
      { code: 'medias:delete', label: 'Supprimer des médias' },
    ],
  },
  {
    label: 'Administration',
    permissions: [
      { code: 'channels:manage', label: 'Gérer les canaux (suspensions, jeton Facebook)' },
      { code: 'users:manage', label: 'Administrer les comptes' },
    ],
  },
];

export const ALL_PERMISSIONS: Permission[] = PERMISSION_GROUPS.flatMap((group) => group.permissions.map((p) => p.code));

export interface Profile {
  label: string
  permissions: Permission[]
}

const READER: Permission[] = ['publications:read', 'campaigns:read', 'medias:read'];
const EDITOR: Permission[] = [...READER, 'publications:write', 'campaigns:write', 'medias:write'];
const PUBLISHER: Permission[] = [
  ...EDITOR, 'publications:verify', 'occurrences:schedule', 'publications:publish', 'publications:delete', 'medias:delete',
];

/**
 * Profils prédéfinis, comme les rôles d'un dépôt GitHub. Ils ne sont pas
 * stockés : en choisir un coche ses droits, que l'on ajuste ensuite un par un.
 */
export const PROFILES: Profile[] = [
  { label: 'Lecteur', permissions: READER },
  { label: 'Rédacteur', permissions: EDITOR },
  { label: 'Diffuseur', permissions: PUBLISHER },
  { label: 'Administrateur', permissions: ALL_PERMISSIONS },
];

/** Le profil dont les droits sont exactement ceux-là, s'il y en a un. */
export function matchingProfile(permissions: readonly Permission[]): Profile | null {
  const set = new Set(permissions);

  return PROFILES.find((profile) =>
    profile.permissions.length === set.size && profile.permissions.every((p) => set.has(p))) ?? null;
}
