# Suppression de Tailwind CSS — migration vers du CSS natif

- **Date** : 2026-07-12
- **Commit** : [`8fa2721`](https://github.com/lpzkjdoi/agorapilot/commit/8fa2721f1eb09ced7c881e66c3e04486fc0a67c3)
- **Branche** : `develop`
- **Périmètre** : `apps/front`

## Contexte

Le frontend Angular utilisait Tailwind CSS 4 : la quasi-totalité du style vivait sous forme de classes utilitaires directement dans les templates HTML, sans aucun CSS écrit dans les fichiers de composants (tous vides). L'objectif était de retirer Tailwind complètement et de repasser en CSS natif par composant, sans changer le rendu visuel.

## Changements

- Analyse des 10 composants Angular de l'app (hiérarchie + classes Tailwind utilisées).
- Conversion des 7 composants ayant des classes Tailwind : classes utilitaires remplacées par des classes sémantiques, avec les règles CSS correspondantes dans le fichier du composant (3 fichiers CSS créés, 2 fichiers existants mais jamais câblés reliés via `styleUrl`).
- Classes dynamiques (`routerLinkActive`, `[class]` avec chaînes Tailwind en ternaire) converties vers les idiomes Angular natifs (`routerLinkActive="active"`, `[class.has-posts]`).
- `styles.css` global : tokens de design (couleurs, radius, shadows) repris en `:root`, reset minimal équivalent au *Preflight* de Tailwind (nécessaire — sans lui, marges par défaut, puces de liste et style natif des boutons réapparaissaient), styles de base (h1-h3, p, label, button).
- Suppression des paquets `tailwindcss`, `@tailwindcss/postcss`, `postcss` et des fichiers `tailwind.config.ts` / `.postcssrc.json`.

## Vérification

- Build Angular (dev et production) propre après chaque étape.
- Vérification visuelle via Chromium/Playwright : dashboard, sidebar (états par défaut/hover/actif), cartes KPI, panneau "publications à venir", calendrier hebdomadaire — y compris les états "avec/sans posts" via des données simulées (interception réseau), pour couvrir les cas non visibles sans backend actif.

## Point notable (hors périmètre)

`dashboard-page.ts` importe un `NotificationsService` qui n'existe nulle part dans l'historique Git — `develop` ne compilait déjà pas avant cette tâche. Un stub temporaire a servi uniquement à débloquer la vérification locale (build + captures d'écran), puis a été supprimé avant le commit. Le vrai correctif reste à faire séparément.
