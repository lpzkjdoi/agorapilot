# 2026-08-22 — Favicon AgoraPilot

## Contexte

Le front servait encore le favicon par défaut généré par le CLI Angular. Maxime a
fourni l'icône officielle AgoraPilot (logo bleu : bulle de dialogue contenant la
mairie, l'église et une maison, avec les ondes de diffusion).

## Changements

- Remplacement de `apps/front/public/favicon.ico` par l'icône fournie.
  Le fichier est une icône Windows 32 bits avec canal alpha, image unique de
  240 × 256 px (254 Ko).

Aucune modification de `apps/front/src/index.html` n'a été nécessaire : la balise
`<link rel="icon" type="image/x-icon" href="favicon.ico">` y était déjà présente,
et le dossier `public/` est copié tel quel à la racine du build par le builder
`@angular/build`.

## Vérification

- Décodage de l'icône (en-tête ICO + DIB) pour contrôler le rendu et la
  transparence avant intégration.
- `cd apps/front && npm test` — suite de tests front verte.

## À noter

Le fichier pèse 254 Ko car il ne contient qu'une seule image non compressée en
240 × 256. Une version multi-tailles (16/32/48 px, payloads PNG) descendrait
autour de 15 Ko sans changement visuel ; à faire si le poids devient gênant.

## Commit

_voir PR_
