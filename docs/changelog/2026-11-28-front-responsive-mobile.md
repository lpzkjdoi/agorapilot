# 2026-11-28 — Front responsive, utilisable au téléphone

## Contexte

Avant la mise en prod, Maxime veut une application 100 % utilisable au
téléphone. État mesuré en 375 px (iPhone) avec un back bouchonné : la page
faisait **1 482 px de large** sur tous les écrans, à cause de la navbar ; le
bandeau de suspension écrasait son texte dans une colonne de 80 px ; le
calendrier était illisible (7 colonnes de 45 px, bulle du jour de 20 rem hors
écran) ; le tableau des comptes et la fiche du canal Facebook débordaient. Le
front ne comptait que 4 media queries.

La maquette Figma ne prévoit aucune version mobile : l'adaptation est conçue
à partir de ses codes (couleurs, composants), en empilant ce qui est côte à côte.

## Changements

Points de rupture retenus : **640 px** (téléphone), **768 / 1024 px**
(tablette), **1280 px** (navbar repliée), **1536 px** (navbar resserrée).

| Zone | Sur petit écran |
|---|---|
| Navbar | Sous 1280 px : bouton burger (`aria-expanded`, `aria-controls`), menu déroulé sous le bandeau avec navigation + compte ; refermé au changement de page et à Échap. Entre 1280 et 1535 px : marges resserrées, icônes masquées, pseudo tronqué — la barre dépassait déjà en 1280–1480 px. |
| Bandeau de suspension | Boutons sous le texte, sur toute la largeur. |
| Modales (base commune `styles.css`) | Actions empilées, action principale en haut, boutons de 44 px ; hauteur en `100dvh` ; champs date/heure qui ne débordent plus. |
| Champs de saisie | 16 px sur écran tactile, pour qu'iOS ne zoome pas au focus (sans bloquer le zoom volontaire). |
| Dashboard | Indicateurs en 2 × 2 ; colonne « Cette semaine » sous les publications à venir (< 1024 px). |
| Publications | Filtres empilés (et ne débordent plus pour une campagne au nom long) ; bouton « Créer » pleine largeur. |
| Calendrier | Cases compactes, les pastilles deviennent des barres de couleur (canal / statut), « +N » sans « autres » ; le détail du jour s'ouvre en panneau ancré en bas de l'écran. Navigation du mois sous le titre. |
| Campagnes | Marges resserrées, onglets défilants, en-têtes qui passent à la ligne. |
| Comptes | Le tableau devient une pile de cartes (libellés repris par `data-label`) ; codes de droits sous leur libellé dans le sélecteur. |
| Canal Facebook | Intitulés au-dessus des valeurs, boutons empilés. |
| Médiathèque | Boutons d'action à la taille du doigt sur écran tactile. |

Le glisser-déposer du calendrier ne fonctionne pas au doigt (API HTML5) : au
téléphone, on déplace une diffusion par « Modifier » dans le panneau du jour.

## Vérification

- Front lancé avec un faux back (Node, port 8080) aux données réalistes —
  noms longs, suspension Facebook active, médias, diffusions en échec — et
  parcouru dans le navigateur intégré : toutes les pages, le menu, les modales
  de programmation (dates précises et répétition), de création, d'ajout de
  publications, de compte, de modification de diffusion, et le panneau du jour.
- Audit scripté de débordement horizontal sur les 8 pages en **320, 375, 768,
  1280 et 1440 px** : aucun élément ne dépasse.
- `npm test` : 68 fichiers, 720 tests verts (nouveaux : menu de la navbar —
  ouverture, fermeture, Échap, navigation — ; libellés `data-label` des
  comptes ; « autres » isolé dans la grille du calendrier).
- `ng build` : aucun budget dépassé par ces changements (seul l'avertissement
  préexistant de `publication-card` subsiste) ; `ng lint` propre.

Non vérifié : sur un vrai téléphone (Safari iOS, Chrome Android) — l'émulation
du navigateur ne rend pas la barre d'adresse mobile ni le clavier virtuel.

## Commit

Voir la PR `claude/responsive-mobile` → `develop`.
