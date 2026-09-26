# Header : reprise du style de la maquette Figma

## Contexte

La maquette Figma de référence
([AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot)) a été
retravaillée : le header (`src/app/components/TopBar.tsx` côté maquette) n'est plus
une barre blanche mais un bandeau bleu foncé, avec des entrées de navigation en
« pills » translucides.

Le composant `NavbarComponent` du front portait encore l'ancien style (fond blanc,
bordure basse, pill bleu clair `#DBEAFE` sur l'entrée active).

## Changements

`apps/front/src/app/core/layout/navbar/navbar.component.css` — CSS repris de la
maquette (classes Tailwind traduites en CSS natif) :

| Élément | Maquette | CSS appliqué |
|---|---|---|
| `header` | `sticky top-0 z-30 bg-[#1E3A8A] shadow-md` | fond `#1E3A8A`, ombre `0 4px 6px -1px / 0 2px 4px -2px rgba(0,0,0,.1)` — plus de bordure basse |
| conteneur | `px-6 h-16 flex items-center gap-6` | `height: 4rem`, `padding: 0 1.5rem`, `gap: 1.5rem` |
| logo | `flex items-center gap-2 shrink-0 mr-2` | `gap: .5rem`, `margin-right: .5rem` |
| badge « AP » | `w-8 h-8 rounded-lg bg-white/15 border border-white/20` | fond et bordure blancs translucides — le dégradé de marque est abandonné |
| libellé « AgoraPilot » | `text-lg font-bold text-white tracking-tight` | blanc plein, `letter-spacing: -0.025em` — plus de dégradé en `background-clip: text` |
| entrée de nav | `gap-2 px-4 py-2 rounded-xl text-sm font-medium border` | `padding: .5rem 1rem`, `border-radius: var(--radius-md)`, bordure 1px, `font-weight: 500` |
| entrée inactive | `bg-white/0 border-transparent text-white/70` | idem |
| entrée survolée | `hover:bg-white/10 hover:border-white/20 hover:text-white` | idem |
| entrée active | `bg-white/20 border-white/30 text-white` | idem |
| icône | `w-4 h-4`, teinte par entrée, blanche si active/survolée | `1rem`, `#93C5FD` (`text-blue-300`, teinte de l'entrée Dashboard), blanche sinon |

Autres ajustements :

- `navbar.component.html` — l'icône passe de 20px à 16px (`w-4 h-4` de la maquette).
- `navbar-button.component.css` (nouveau) — le libellé hérite de la couleur du
  bouton et prend `font-weight: 500` ; sans cela la règle globale sur `p`
  (`font-weight: 400`) l'emportait sur les états de la pill.

Les boutons du bandeau présents dans la maquette mais absents du front
(notifications, séparateur, bloc utilisateur, avatar) n'ont volontairement pas été
créés : seul le style des éléments existants a été repris.

## Vérification

- `cd apps/front && npm test` → 13 fichiers, 52 tests verts. Deux tests ajoutés sur
  `NavbarComponent` (couleur de fond `rgb(30, 58, 138)`, icône en 16px) et un sur
  `NavbarButtonComponent` (libellé en `font-weight: 500`, couleur héritée).
- Rendu contrôlé sur `http://localhost:4200/dashboard` et comparé à l'aperçu de la
  maquette Figma.

## Commit

_voir PR_
