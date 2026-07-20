# AgoraPilot — conventions projet

## Tests front (Angular) — obligatoire

Chaque composant du front (`apps/front`) doit avoir un fichier de test unitaire
`*.spec.ts` à côté de lui.

**Règle à appliquer systématiquement :** dès qu'un composant est **créé** ou
**modifié**, son `*.spec.ts` doit être **ajouté** ou **mis à jour** dans le même
changement. Un composant sans test, ou dont le test ne reflète plus le
comportement après modification, est considéré comme incomplet.

- Framework : Karma + Jasmine (config `apps/front/karma.conf.js`).
- Lancer la suite : `cd apps/front && npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox`
  (le lanceur `ChromeHeadlessNoSandbox` ajoute `--no-sandbox`, requis en CI et en conteneur ;
  en local, exporter `CHROME_BIN` vers un binaire Chromium si Chrome n'est pas installé).
- La CI **Front CI** (`.github/workflows/front-ci.yml`) exécute ces tests à chaque
  push et à chaque pull request vers `main` ou `develop`. Les tests doivent être
  verts avant de fusionner vers `develop`.

Points d'attention rencontrés (utiles pour écrire de nouveaux tests) :

- Inputs `input.required` / signaux : les définir via `fixture.componentRef.setInput(...)`
  avant `detectChanges()`.
- Composants utilisant `RouterLink` / `RouterLinkActive` : fournir `provideRouter([])`.
- `DatePipe` avec la locale `fr-FR` : enregistrer la locale dans le spec
  (`registerLocaleData(localeFr, 'fr-FR')`) sinon le rendu lève une erreur.
