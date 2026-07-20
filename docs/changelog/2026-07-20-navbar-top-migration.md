# Migration de la barre de navigation — de la gauche vers le haut

- **Date** : 2026-07-20
- **Branche** : `claude/session-xfe1tv` (depuis `develop`)
- **Périmètre** : `apps/front`
- **Référence design** : [maquette Figma AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot) (`TopBar.tsx`)

## Contexte

La maquette Figma a fait évoluer la navigation principale : l'ancienne *sidebar* verticale ancrée à gauche (grille `1fr 4fr`) est remplacée par une barre horizontale *sticky* en haut de l'écran (`TopBar`). L'objectif de cette tâche est de migrer l'UI Angular vers cette nouvelle disposition, en restant fidèle à la maquette pour le style, tout en respectant le périmètre réel de l'application.

## Décisions de périmètre

La maquette présente 6 entrées de navigation et une zone utilisateur à droite (cloche de notification, nom/rôle, avatar). L'application Angular n'expose aujourd'hui qu'**une seule route** (`/dashboard`) et n'a ni notifications ni authentification. Après validation :

- **Entrées de nav** : seule l'entrée `Dashboard` (fonctionnelle) est rendue. Les 5 autres (Campagnes, Calendrier, Publications, Médiathèque, Paramètres) sont hors périmètre tant que les routes correspondantes n'existent pas.
- **Zone droite** : non reprise pour l'instant (pas de logique de notifications/utilisateur dans l'app). La barre se limite au logo et à la navigation.

## Changements

- **Renommage `sidebar` → `navbar`** (via `git mv`, historique conservé) : dossier `core/layout/sidebar` → `core/layout/navbar`, composants `SidebarComponent` → `NavbarComponent`, `SidebarButtonComponent` → `NavbarButtonComponent`, sélecteurs `app-sidebar` → `app-navbar` et `button[sideBarButton]` → `button[navBarButton]`.
- **`navbar.component.html`** : nouvelle structure horizontale — logo (badge dégradé « AP » + libellé « AgoraPilot » en texte dégradé, repris de la maquette) puis liste de navigation. Icône du bouton passée de 24px à 20px pour l'échelle d'une barre haute.
- **`navbar.component.css`** : barre `sticky top:0` (`z-index:30`), hauteur `4rem`, fond `--color-ui-card`, `border-bottom` + `--shadow-card` ; logo via `--background-image-brand-gradient` (badge et texte `background-clip:text`) ; boutons en ligne, états `hover`/`active` conservés (`#DBEAFE` / `#1D4ED8`, cohérents avec l'ancienne sidebar).
- **`app.component.*`** : passage de la grille `1fr 4fr` à un `flex` colonne (`min-height:100vh`) — navbar en haut, contenu (`.content`, `flex:1`) en dessous et pleine largeur. Import mis à jour vers `NavbarComponent`.

## Vérification

- `npm run build` (Angular 20, configuration production) : build propre, aucune référence résiduelle à `sidebar`.
- Vérification visuelle via Chromium (`ng serve`) sur `/dashboard` : barre horizontale en haut, logo dégradé conforme à la maquette, entrée Dashboard en état actif (pastille bleu clair), contenu du dashboard rendu en pleine largeur sous la barre.
