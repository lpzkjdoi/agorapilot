# Page Publications

## Contexte

La maquette Figma de référence
([AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot?preview-route=%2Fposts))
décrit une page « Publications » que le front n'avait pas encore : seuls le
`PublicationsService` (branché sur `GET`/`POST /api/publications`) et un
`PublicationFormComponent` non utilisé existaient. La navbar ne proposait que
l'entrée « Dashboard ».

## Changements

### Route et navigation

- `app.routes.ts` — nouvelle route `publications` (titre « Publications »).
- `navbar.component.html` — deuxième entrée de navigation « Publications »,
  bâtie sur le `NavbarButtonComponent` existant.

### Page (`features/publications/pages/publications-page`)

Reprise de la maquette :

| Élément | Rendu |
|---|---|
| En-tête | titre (`h1` global) + sous-titre « Gérez et organisez vos publications », bouton primaire « Créer une publication » (`#1E3A8A`, comme le bandeau) |
| Filtres | carte blanche : recherche plein texte, sélecteur de statut, sélecteur de campagne |
| Compteurs | bandeau clair : Total / Vérifiées / Brouillons / Dans campagnes, pastilles bleue, verte, orange, violette |
| Séparateur | « N PUBLICATIONS » centré entre deux filets |
| Grille | 3 colonnes sur écran large (`auto-fill` / `minmax(min(100%, 22rem), 1fr)`), puis 2 et 1 |

Le titre reste noir (`h1` global) plutôt que bleu comme sur l'aperçu de la page
Publications : le Dashboard de la maquette affiche bien un titre noir surmontant
un sous-titre gris, et la v33 demande explicitement un titre « identique au
Dashboard ».

Le `h1` global de `styles.css` passe au passage de 28px/36px à **24px/32px**, le
`text-2xl` de la maquette. Le changement est global et vaut donc aussi pour le
Dashboard, qui utilise la même règle — les deux pages partagent désormais le
gabarit de titre de la maquette. Ce point n'est pas couvert par un test : sous
jsdom, seuls les styles de composant (injectés par Angular) sont appliqués, la
feuille globale ne l'est pas — `getComputedStyle(h1).fontSize` y renvoie le `2em`
de la feuille par défaut. La vérification a été faite dans le navigateur.

États de la liste : chargement, erreur de chargement, **« Aucune publication
disponible »** quand l'API renvoie une liste vide, et « Aucune publication ne
correspond à ces filtres » quand seuls les filtres vident la grille.

### Composants

- `publication-filters` — recherche + deux sélecteurs + bandeau de compteurs.
  Les libellés des champs existent mais sont réservés aux lecteurs d'écran, la
  maquette n'affichant que les contrôles.
- `publication-card` — vignette, badge de statut (`Vérifié` vert / `Brouillon`
  orange), date, extrait sur 3 lignes, pastille de campagne, puis les trois
  actions empilées : **Générer XLSX**, **Publier sur Facebook** (bleu `#1877F2`
  de la charte), **Ajouter à une campagne** (contour pointillé quand aucune
  campagne n'est rattachée, « Changer de campagne » sinon).
- `create-publication-modal` — modale du bouton « Créer une publication »,
  fermable au fond, à la croix, au bouton « Annuler » et à la touche Échap ;
  elle enveloppe le `PublicationFormComponent`, restylé au passage (zone de
  texte, compteur de caractères, case « vérifiée », actions).

### Services

- `CampaignsService` (nouveau) — `GET /api/campaigns`, alimente le filtre par
  campagne.
- `PublicationsService` — les trois actions de la carte n'ont **pas** d'endpoint
  côté back. Elles sont déclarées avec un `TODO(back)` décrivant l'appel attendu
  et échouent explicitement, plutôt que de simuler un succès ; la page affiche
  alors « Cette action n'est pas encore disponible. »

| Action | Endpoint attendu |
|---|---|
| `generateXlsx` | `POST /api/publications/{id}/xlsx` |
| `publishOnFacebook` | `POST /api/publications/{id}/deliveries` avec `{ channel: 'FACEBOOK' }` |
| `assignToCampaign` | `PATCH /api/publications/{id}` avec `{ campaignId }` |

Le choix de la campagne se fera dans une modale dédiée, livrée avec l'endpoint
de rattachement.

### Limites côté données

`PublicationDTO` n'expose que `id`, `content` et `status`. La maquette affiche en
plus une date et une campagne, et une vignette média. Le modèle front porte donc
`createdAt` et `campaign` en optionnels (documentés dans `publication.model.ts`)
et la carte se replie proprement en leur absence ; la vignette reste dans son
état « sans média », la médiathèque n'existant pas encore côté domaine.

## Vérification

- `cd apps/front && npm test` → 18 fichiers, 99 tests verts (7 nouveaux fichiers
  de spec, specs `navbar`, `app.routes`, `publications.service` et
  `publication-form` mis à jour).
- `npm run lint` → vert.
- `npm run build` → vert, sans dépassement de budget CSS.
- Rendu contrôlé sur `http://localhost:4200/publications` (liste, filtres,
  compteurs, modale, message d'action indisponible, état « Aucune publication
  disponible ») et comparé à l'aperçu de la maquette Figma.

## Commit

_voir PR_
