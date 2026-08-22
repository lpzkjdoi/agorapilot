/**
 * Identité du build, affichée par le badge d'environnement du bandeau.
 *
 * Les valeurs ci-dessous sont celles du développement local. Ce fichier est
 * réécrit **par la CI**, juste avant la construction de l'image front (étape
 * « Renseigne l'identité du build » de `.github/workflows/deploy-preprod.yml`) :
 * l'image porte donc l'environnement qu'elle sert, sans variable à fournir au
 * démarrage du conteneur.
 *
 * `version` n'est affichée qu'en production, où elle vaut le tag SemVer posé
 * sur `main` (cf. [docs/gitflow.md](../../../../docs/gitflow.md)) ; en `dev` et
 * en `preprod`, c'est le nom de l'environnement qui est montré.
 */
export type AppEnvironmentName = 'dev' | 'preprod' | 'prod';

export interface BuildInfo {
  environment: AppEnvironmentName;
  version: string;
}

export const buildInfo: BuildInfo = {
  environment: 'dev',
  version: 'dev',
};
