# Migration des tests front de Karma/Jasmine vers Vitest

**Date** : 2026-07-25
**Branche** : `claude/migration-vitest`

## Contexte

Le front testait sous Karma + Jasmine, l'outillage historique d'Angular. Karma est déprécié
depuis Angular 16 et son principal coût ici était opérationnel : la suite exigeait un
navigateur. D'où le lanceur `ChromeHeadlessNoSandbox` de `karma.conf.js`, la variable
`CHROME_BIN` à exporter en local, et l'impossibilité de faire tourner les tests sur un poste
sans Chrome — c'est exactement ce qui a bloqué les vérifications des trois dernières tâches
(voir la section « Non vérifié sur le poste de développement » des changelogs du 2026-07-25).

Angular 20 fournit le builder `@angular/build:unit-test`, qui sait piloter Vitest. C'est la
voie officielle et celle par défaut des nouveaux projets.

## Changements

### Bascule sur les builders `@angular/build`

`angular.json` utilisait `@angular-devkit/build-angular`, paquet de compatibilité qui
réexporte `@angular/build` en y ajoutant les outils historiques (Karma, webpack). Le builder
`unit-test` vit dans `@angular/build` : les quatre cibles (`build`, `serve`, `extract-i18n`,
`test`) pointent désormais directement dessus, et `@angular-devkit/build-angular` est retiré
des dépendances.

La cible `test` devient :

```json
"test": {
  "builder": "@angular/build:unit-test",
  "options": {
    "buildTarget": "front:build:development",
    "tsConfig": "tsconfig.spec.json",
    "runner": "vitest"
  }
}
```

Le builder réutilise le graphe de build de l'application : les specs sont compilées par le
même esbuild/AOT que le code de production, et non par une configuration de test parallèle.
Sans option `browsers`, il démarre Vitest en **jsdom** — plus aucun navigateur requis. Il
initialise lui-même le `TestBed` (`errorOnUnknownElements` et `errorOnUnknownProperties`
activés) et démarre Vitest avec `globals: true`.

### Dépendances

Retirées : `@angular-devkit/build-angular`, `karma`, `karma-chrome-launcher`,
`karma-coverage`, `karma-jasmine`, `karma-jasmine-html-reporter`, `jasmine-core`,
`@types/jasmine`. `karma.conf.js` est supprimé.

Ajoutées : `@angular/build`, `vitest` (`^3.2.4` — la plage exigée par le peer de
`@angular/build` 20.3 est `^3.1.1`, Vitest 4 n'est pas encore supporté par cette version du
builder), `jsdom`, `@vitest/coverage-v8`.

`tsconfig.spec.json` passe de `"types": ["jasmine"]` à `"types": ["vitest/globals"]`.

### Scripts et CI

`npm test` suffit désormais : le mode surveillance est désactivé d'office hors TTY. Deux
scripts s'ajoutent, `test:watch` et `test:coverage` (`ng test --code-coverage`, provider v8).
L'étape « Unit tests » de `front-ci.yml` perd ses options Karma.

### Adaptation des specs existantes

Le portage a été indolore : 512 lignes de specs, deux constructions Jasmine seulement.

- `spyOn(console, 'error')` → `vi.spyOn(console, 'error').mockImplementation(...)`. Le
  `mockImplementation` est ajouté volontairement : Jasmine remplaçait la fonction espionnée
  par défaut, Vitest la laisse s'exécuter — sans lui, le test polluerait la sortie avec la
  vraie erreur.
- `jasmine.any(Error)` → `expect.any(Error)`.
- `toBeTrue()` / `toBeFalse()` (matchers propres à Jasmine) → `toBe(true)` / `toBe(false)`,
  9 occurrences dans 2 fichiers.

### Tests ajoutés

La règle « un `*.spec.ts` par composant » de `CLAUDE.md` laissait les services et le routage
hors couverture. Trois specs sont ajoutés, soit 12 tests :

- `occurrences.service.spec.ts` : URL et méthode de `GET /api/occurrences/weekly`, absence de
  requête tant que l'observable n'est pas souscrit (piège classique des `Observable` froids),
  propagation d'une erreur 500 au souscripteur.
- `publications.service.spec.ts` : `GET` de la liste, corps du `POST` de création transmis
  tel quel, paresse des deux observables, propagation d'un 400.
- `app.routes.spec.ts` : la route `dashboard` rend bien `DashboardPageComponent` et
  positionne le titre du document ; une URL inconnue est rejetée, ce qui documente l'absence
  volontaire (pour l'instant) de route de repli.

`CLAUDE.md` est mis à jour en conséquence : la règle de test couvre explicitement les
services, et les points d'attention mentionnent les globales Vitest et le trio
`provideHttpClient` / `provideHttpClientTesting` / `HttpTestingController`.

## Vérification effectuée

**Sur le poste de développement, sans Chrome installé** — ce qui était précisément
impossible avant :

- `npm test` → **13 fichiers, 49 tests, tous verts** en 3,2 s (37 tests avant migration).
- `npm run test:coverage` → rapport v8 produit, suite verte.
- `npm run build -- --configuration production` → succès sous `@angular/build:application`,
  300,77 kB initiaux (84,20 kB transférés), sortie inchangée dans `dist/front`.
- `npm run lint` → 6 erreurs, **toutes préexistantes** et sans rapport avec la migration
  (préfixe de sélecteur `navbar-button`, deux `label` sans contrôle associé, trois
  `type` vs `interface` dans les modèles). Aucun des specs, anciens ou nouveaux, n'est
  signalé. Le lint reste non bloquant en CI.

Non vérifié : `ng serve` sous le nouveau builder `@angular/build:dev-server` n'a pas été
lancé bout en bout ; le build de production emprunte la même chaîne et passe.

## Suite

Deux anomalies repérées en passant, hors périmètre de cette tâche :

- `WeeklyOccurrences` est déclaré `Map<string, Occurrence[]>` alors que le back renvoie un
  objet JSON — `HttpClient` ne construira jamais de `Map`. Le spec du service documente le
  comportement réel avec un commentaire explicite.
- `PublicationFormComponent.onSubmit()` contient un `console.log("ERROR DANS LE EMIT")`
  oublié, visible dans la sortie des tests.
