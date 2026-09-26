# Tests unitaires pour tous les composants front + convention

- **Date** : 2026-07-20
- **Branche** : `claude/session-xfe1tv` (depuis `develop`)
- **Périmètre** : `apps/front`, `.github/workflows/front-ci.yml`, `CLAUDE.md`

## Contexte

Le front Angular ne disposait que d'un seul fichier de test (`app.component.spec.ts`), généré par le CLI et **cassé** (il vérifiait un `h1` « Hello, front » inexistant). Aucun composant métier n'était couvert. L'objectif : couvrir **tous** les composants par des tests unitaires, garantir leur exécution en CI sur chaque PR vers `develop`, et formaliser la règle « un composant créé/modifié ⇒ son test ajouté/mis à jour ».

## Changements

### Tests (10 composants couverts)

Un `*.spec.ts` par composant, à côté du composant :

- `AppComponent` — spec réécrit (l'ancien testait un contenu inexistant) : création, titre, rendu de `app-navbar` + `router-outlet`.
- `NavbarComponent` — logo (badge « AP » + wordmark), entrée Dashboard câblée à `/dashboard`.
- `NavbarButtonComponent` — rendu du libellé (`name`), réactivité au changement d'input.
- `DashboardKpiComponent` — titre, valeur, couleur du badge d'icône.
- `UpcomingPostsComponent` — état vide (« Aucune publication prévue ») vs une carte par occurrence.
- `UpcomingPostCardComponent` — rendu id + contenu, tolérance à l'absence d'occurrence.
- `WeeklyCalendarComponent` — `entries` calculé (vide / mappé), rendu de 7 cartes.
- `WeeklyOccurrenceCardComponent` — `date`/`numberOfPosts`/`hasPosts`, classe `has-posts`, pluriel.
- `DashboardPageComponent` — service mocké : KPIs + panneau, calendrier affiché seulement quand les données arrivent, erreur de chargement loguée sans crash.
- `PublicationFormComponent` — validité initiale, non-émission si invalide (+ `markAllAsTouched`), émission + reset si valide, borne des 2000 caractères.

**Résultat : 37 tests, tous verts.**

### Configuration Karma

- Ajout de `apps/front/karma.conf.js` avec un lanceur `ChromeHeadlessNoSandbox`
  (`--no-sandbox --disable-gpu --disable-dev-shm-usage`) pour fiabiliser l'exécution
  en CI (runners GitHub) et en conteneur.
- `angular.json` : `test.options.karmaConfig` pointe vers ce fichier.
- `front-ci.yml` : l'étape « Unit tests » utilise `--browsers=ChromeHeadlessNoSandbox`.

### Convention

- Ajout de `CLAUDE.md` à la racine : règle rendant obligatoire l'ajout/mise à jour
  du `*.spec.ts` à chaque composant créé/modifié, + rappels pratiques (inputs signaux,
  `provideRouter`, locale `fr-FR` pour `DatePipe`).

## Vérification

- `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox` : **37 succès, 0 échec**.
- Les tests se déclenchent en CI via **Front CI** sur push/PR vers `main`/`develop`
  (chemin `apps/front/**`). Pour une PR ciblant `develop`, GitHub lit le workflow
  depuis `develop`, où `front-ci.yml` est présent : les tests tournent donc bien.

## Point notable (hors périmètre)

`PublicationFormComponent.onSubmit()` contient un `console.log("ERROR DANS LE EMIT")`
de debug (visible dans la sortie des tests). Laissé tel quel — nettoyage à faire
séparément.
