# Loader de la maquette et états de chargement

## Contexte

La maquette Figma de référence
([AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot))
s'est enrichie d'un composant `src/app/components/Loader.tsx`, en deux variantes
(fond clair / fond foncé). Le front n'avait rien d'équivalent : l'attente d'une
requête était soit muette, soit rendue par une phrase posée dans le flux
(« Chargement des publications… »).

Le connecteur Figma de la session ne donne accès au contenu des fichiers d'un
Figma **Make** que sous forme de liens de ressources, qu'il ne sait pas relire ;
et le bundle du preview ne contient pas le composant, encore inutilisé dans la
maquette donc éliminé au tree-shaking. La spécification a donc été reprise du
journal de construction de la maquette (versions 37 et 38), qui la décrit
intégralement :

- anneau SVG scalable, `stroke-width` exprimé en unités du `viewBox` donc
  proportionnel à la taille rendue ;
- trois tailles : `sm` 20px, `md` 36px (défaut), `lg` 56px ;
- arc couvrant 72 % du cercle, extrémités arrondies ;
- rotation 360° en 0,9 s, linéaire, infinie, en CSS pur ;
- variante `light` (défaut) — arc `#1E3A8A`, piste `#1E3A8A` à 12 % ;
- variante `dark` — arc blanc, piste blanche à 20 %, calibrée pour le fond
  `#1E3A8A` du header et des boutons.

## Changements

### `core/loader` (nouveau)

| Fichier | Rôle |
|---|---|
| `loader.component.ts` | le composant, les types `LoaderSize` et `LoaderVariant` |
| `loader.component.html` | l'anneau SVG (piste + arc) et le libellé lu par les lecteurs d'écran |
| `loader.component.css` | tailles, variantes et animation |

```html
<app-loader />                                  <!-- md, light -->
<app-loader size="lg" label="Chargement…" />
<app-loader size="sm" variant="dark" />
```

- `size` — `'sm' | 'md' | 'lg'`, défaut `'md'`.
- `variant` — `'light' | 'dark'`, défaut `'light'`.
- `label` — texte annoncé, défaut « Chargement en cours… ». L'hôte porte
  `role="status"` ; le SVG est `aria-hidden`, seul le libellé est lu.

Les 72 % de l'arc sont obtenus par `pathLength="100"` sur le cercle, ce qui rend
le `stroke-dasharray: 72 28` du CSS directement lisible en pourcentage de la
circonférence. Sous `prefers-reduced-motion: reduce`, la rotation est ralentie à
2,4 s plutôt que supprimée : l'attente reste lisible.

### Branchement aux requêtes

| Endroit | Avant | Après |
|---|---|---|
| Dashboard — `getWeeklyOccurrences()` | le signal `loading` existait mais n'était affiché nulle part | `app-loader` en `lg` à la place du calendrier de la semaine |
| Publications — `getPublications()` | « Chargement des publications… » | `app-loader` en `lg`, centré |
| Publications — `createPublication()` | aucun retour pendant l'appel | `app-loader` en `sm`/`dark` dans le bouton d'envoi, les deux boutons désactivés |

Deux corrections viennent avec le branchement :

- Dashboard : `tap` remplacé par `finalize`, pour que le loader s'arrête aussi
  quand le flux se termine sans rien émettre, et pas seulement sur une réponse ou
  une erreur.
- Formulaire de publication : `onSubmit()` ignore un second envoi tant que le
  premier est en vol — sans quoi rendre l'attente visible aurait laissé la porte
  ouverte à deux publications créées d'un double clic.

Les actions `XLSX`, `Facebook` et « assigner une campagne » n'ont pas de loader :
leurs endpoints n'existent pas encore et l'appel échoue immédiatement.

## Vérification

```bash
cd apps/front && npm test
```

23 fichiers, 155 tests verts, dont les 6 du nouveau `loader.component.spec.ts`
(tailles, variantes, géométrie de l'arc, libellé accessible) et 7 tests ajoutés
aux specs des composants touchés (page Dashboard, page Publications, modale et
formulaire de création).

`npx ng build` passe. Le rendu de l'anneau — les deux variantes aux trois
tailles, puis le bouton d'envoi en cours d'appel — a été vérifié visuellement
dans un navigateur.

## Commit

_voir PR_
