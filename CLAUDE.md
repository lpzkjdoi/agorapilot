# CLAUDE.md — Règles de fonctionnement AgoraPilot

Ce fichier est chargé automatiquement à chaque session. Il définit les règles à
respecter **systématiquement**, sans avoir besoin qu'on me les redemande.

## Workflow Git & PR

- **Sauf mention explicite du contraire, toute tâche part de `develop`.**
  Créer la branche de travail depuis `develop` à jour
  (`git fetch origin develop && git checkout -B claude/<desc> origin/develop`).
- **Toujours** développer sur une branche dédiée nommée `claude/<description-courte>`.
  Ne jamais committer directement sur `develop` ni `main`.
- **Ouvrir automatiquement une Pull Request en draft vers `develop`** après le push,
  s'il n'en existe pas déjà une d'ouverte pour la branche.
- Messages de commit clairs et descriptifs (impératif, en français).
- Le modèle de branches, le cycle de contribution et le versionnage sont décrits
  dans [`docs/gitflow.md`](./docs/gitflow.md).

## Tests — systématiques

Toute évolution du code s'accompagne de tests. Ne jamais livrer de code non testé.

### Backend (`apps/back`) — Java 21 / Spring Boot, JUnit

```bash
cd apps/back && mvn -B verify
```

C'est ce que vérifie la CI **Back CI** (`.github/workflows/ci.yml`) : les tests
back doivent passer avant tout push.

### Frontend (`apps/front`) — Angular 20, Vitest — obligatoire

Chaque composant du front doit avoir un fichier de test unitaire `*.spec.ts` à
côté de lui. **Dès qu'un composant est créé ou modifié, son `*.spec.ts` doit être
ajouté ou mis à jour dans le même changement.** Un composant sans test, ou dont le
test ne reflète plus le comportement après modification, est considéré comme
incomplet. La même règle vaut pour les services.

```bash
cd apps/front && npm test
```

Les tests tournent sous **Vitest**, via le builder `@angular/build:unit-test`, dans
un environnement **jsdom** : aucun navigateur ni `CHROME_BIN` n'est requis, ni en
local ni en CI. `npm run test:watch` pour le mode surveillance,
`npm run test:coverage` pour la couverture. La CI **Front CI**
(`.github/workflows/front-ci.yml`) exécute ces tests à chaque push et PR vers
`main` ou `develop` ; ils doivent être verts avant de fusionner vers `develop`.

Points d'attention pour écrire de nouveaux tests front :

- Les globales (`describe`, `it`, `expect`, `vi`) sont fournies par le builder,
  sans import. Utiliser `vi.fn()` / `vi.spyOn()` — les API `jasmine.*` et les
  matchers `toBeTrue()` / `toBeFalse()` n'existent pas ; écrire `toBe(true)`.
- Inputs `input.required` / signaux : les définir via
  `fixture.componentRef.setInput(...)` avant `detectChanges()`.
- Composants utilisant `RouterLink` / `RouterLinkActive` : fournir `provideRouter([])`.
- `DatePipe` avec la locale `fr-FR` : enregistrer la locale dans le spec
  (`registerLocaleData(localeFr, 'fr-FR')`) sinon le rendu lève une erreur.
- Services HTTP : `provideHttpClient()` + `provideHttpClientTesting()`, puis
  `HttpTestingController` ; terminer par `httpTesting.verify()`.

## Documentation — systématique

Une entrée de changelog est ajoutée **à chaque tâche de développement significative** :

- Créer `docs/changelog/AAAA-MM-JJ-sujet.md` : contexte, changements, vérification
  effectuée, lien vers le commit.
- Mettre à jour le tableau récapitulatif dans `docs/README.md`.

## Design / maquette (référence UI)

Maquette Figma de référence :
<https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot?p=f&t=VCDVoJru7FfhBeIj-0>

**Quand on me demande d'implémenter un élément ou un composant d'interface, ou
qu'on fait référence à « la maquette », je dois m'appuyer sur cette maquette
Figma** (mise en page, espacements, couleurs, composants) pour reproduire
fidèlement le design attendu.

## Stack technique (rappel)

- **Backend** `apps/back` : Java 21, Spring Boot 4, Spring Data JPA, Spring Security,
  PostgreSQL (H2 en test), Lombok, springdoc-openapi. Build : Maven (`./mvnw`).
- **Frontend** `apps/front` : Angular 20 (standalone, signals), TypeScript strict, RxJS.

## Domaine métier

Publications → Occurrences (diffusions planifiées) → Livraisons (par canal :
Facebook Page via Graph API ; Intramuros à venir). Campagnes = regroupement de
publications limité dans le temps.
