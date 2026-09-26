# Indicateur d'environnement dans le bandeau

- **Date** : 2026-08-22
- **Branche** : `claude/indicateur-environnement` (depuis `develop`)
- **Périmètre** : `apps/front`, `.github/workflows/deploy-preprod.yml`
- **Référence design** : [maquette Figma AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot)
  (`TopBar.tsx`, version 29 « Add version indicator badge »)

## Contexte

Rien dans l'interface ne disait sur quel environnement on se trouvait. Trois
environnements coexistent : `dev` (poste de développement), `preprod` (VPS) et
`prod` (à venir). Un badge discret dans le bandeau lève l'ambiguïté, en
particulier quand plusieurs onglets sont ouverts côte à côte.

La maquette traite déjà le sujet. Son intention, telle qu'énoncée dans le fil de
la version 29 :

> Un petit badge `dev` en monospace, `text-white/30` avec bordure
> `border-white/10` — quasi invisible mais lisible si on cherche. Il suffit de
> changer la valeur (`preprod`, `1.2.0`, etc.) selon l'environnement.

Le badge y est posé tout à droite du bandeau, après l'avatar (absent de notre
front, hors périmètre depuis la migration de la navbar).

## Changements

### Identité du build — `apps/front/src/environments/build-info.ts` (nouveau)

Un fichier dédié porte `{ environment, version }`, avec les valeurs du
développement local (`dev` / `dev`) et le type `AppEnvironmentName`
(`'dev' | 'preprod' | 'prod'`).

Ce fichier est **réécrit par la CI** juste avant la construction de l'image
front : l'artefact porte l'environnement qu'il sert, sans variable à fournir au
démarrage du conteneur ni appel réseau supplémentaire au chargement.

Ce choix rompt avec la propriété « une image identique d'un environnement à
l'autre » qui vaut pour `environment.ts` (l'`apiUrl` y est relative
justement pour cela) : l'image front devient spécifique à sa cible. C'est
assumé — l'indicateur n'a de sens que s'il est spécifique, et la CI publie déjà
une image par commit.

### Badge — `core/layout/navbar/components/environment-badge/` (nouveau)

`EnvironmentBadgeComponent`, `<app-environment-badge />`, ajouté en dernier
enfant du `<header>`. `.navbar-nav` porte `flex: 1`, le badge est donc poussé
contre le bord droit, comme dans la maquette.

L'identité du build est une **entrée** (`input<BuildInfo>(buildInfo)`) et non une
lecture directe du module : le badge reste testable sur les trois environnements
sans simulation.

Ce qu'il affiche :

| Environnement | Badge | Pourquoi |
|---|---|---|
| `dev` | `dev` | le nom lève l'ambiguïté |
| `preprod` | `preprod` | idem |
| `prod` | `1.2.0` | l'environnement y est implicite, c'est la release qui informe |

Style repris de la maquette (classes Tailwind traduites en CSS natif, comme le
fait déjà `navbar.component.css`) :

| Élément | Maquette | CSS appliqué |
|---|---|---|
| police | `font-mono` | `ui-monospace, SFMono-Regular, Menlo, Consolas, monospace` |
| taille | `text-[10px]` | `font-size: 10px`, `line-height: 14px` |
| couleur | `text-white/30` | `rgba(255, 255, 255, .3)` |
| bordure | `border border-white/10` | `1px solid rgba(255, 255, 255, .1)` |
| espacement | `px-1.5 py-0.5` | `padding: .125rem .375rem` |
| rayon | `rounded-md` (6px) | `var(--radius-sm)` (8px) |

Seul écart : le rayon. Le jeu de tokens du projet ne descend pas sous 8px, et
l'écart est invisible sur un badge de 20px de haut.

### CI — `.github/workflows/deploy-preprod.yml`

Nouvelle étape « Renseigne l'identité du build », conditionnée à
`matrix.service == 'front'` et placée avant `Build & push`. Elle réécrit
`build-info.ts` en entier (plutôt qu'un `sed` sur deux valeurs : une évolution
du fichier casse alors le build au lieu de passer silencieusement) avec
`environment: 'preprod'` et le SHA court en `version`.

Il n'existe pas encore de workflow de production. Quand il arrivera, il écrira
le même fichier avec `prod` et le tag SemVer posé sur `main`
(`${GITHUB_REF_NAME#v}`, cf. [`docs/gitflow.md`](../gitflow.md)) — c'est cette
valeur que le badge affichera.

## Vérification

- `cd apps/front && npm test` → 24 fichiers, 168 tests verts. 7 tests ajoutés sur
  `EnvironmentBadgeComponent` (repli sur l'identité du build, libellé pour
  chacun des trois environnements, réaction à un changement d'entrée, style de la
  maquette) et 1 sur `NavbarComponent` (badge en dernier enfant du bandeau).
- `npm run lint` → propre. `npm run build -- --configuration production` → OK.
- Rendu contrôlé sur le build de production servi en local : badge mesuré à
  **32,06 × 20 px, à 24 px du bord droit** — la maquette mesure 31 × 20 px à
  24 px du bord. Couleurs et police conformes.
- Bloc `run:` de la nouvelle étape CI rejoué localement : le fichier généré est
  correctement désindenté et compile.

## Commit

_voir PR_
