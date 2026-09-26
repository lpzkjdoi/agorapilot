# Page Campagnes

**Date** : 2026-09-22
**Périmètre** : `apps/front` (page dédiée), `apps/back` (contrat de lecture)

## Contexte

Les campagnes existaient de bout en bout sauf à l'écran : le back exposait
`GET` et `POST /api/campaigns`, le front portait un modèle et un service, mais
la seule chose qu'on en voyait était la liste déroulante du filtre « campagne »
de la page Publications. La maquette Figma décrit pourtant une page `campaigns`
complète, et la navbar n'avait que trois entrées.

En préparant cette page, `GET /api/campaigns` s'est révélé porteur du même
défaut que celui corrigé sur `GET /api/occurrences/weekly` — voir
[le correctif du 2026-08-22](./2026-08-22-fix-recursion-occurrences-weekly.md).

## Changements

### Back — `GET /api/campaigns` renvoie des DTO

L'endpoint sérialisait l'entité `Campaign`. Ses publications référencent en
retour leur campagne (`Publication.campaign`, sans annotation Jackson) : dès
qu'une campagne porte une publication, Jackson part dans un cycle sans fin et
lève après avoir commencé à écrire la réponse — le client reçoit un `200` dont
le corps ne se parse pas. Le défaut existait depuis l'écriture de l'endpoint et
ne s'était encore jamais manifesté, faute de campagne peuplée.

`CampaignService.getAll()` applique désormais `CampaignMapper`, déjà écrit pour
la création, et renvoie des `CampaignDTO`. Au passage, `archived`, `createdAt`
et `updatedAt` cessent d'être exposés : le front ne les déclarait pas.

Les campagnes n'avaient **aucun test** : le service et le contrat HTTP sont
maintenant couverts.

### Front — la page `/campagnes`

- Entrée « Campagnes » dans la navbar, entre Dashboard et Publications, à la
  place que lui donne la maquette.
- Liste des campagnes : recherche par nom, bouton de création, et pour chaque
  campagne son nom, son statut, sa date de début et son nombre de publications.
  La campagne sélectionnée porte le fond lavande et la barre bleue de la
  maquette ; la première de la liste est sélectionnée à l'arrivée.
- Détail de la campagne : nom, statut, période, description, trois compteurs
  (publications, vérifiées, brouillons) et la liste des publications rattachées
  avec leur statut.
- Modale de création : nom, description, dates de début et de fin, branchée sur
  `POST /api/campaigns`.

Le badge de statut d'une campagne vit dans `styles.css` : la liste et le détail
le partagent, comme l'habillage des modales avant lui.

## Écarts assumés avec la maquette

La maquette montre des éléments que le back n'expose pas. Plutôt que de les
simuler, ils sont laissés de côté :

- **Thème et couleur de campagne** (champs de la modale, badge du détail) :
  absents de `Campaign` côté back.
- **Colonnes « Channel » et « Scheduled Date »** du tableau des publications, et
  compteurs « Published » / « Scheduled » : ils décrivent les occurrences et
  leurs livraisons, donc la planification — hors périmètre ici. Les compteurs
  affichés dérivent de `PublicationStatus` (`DRAFT` / `VERIFIED`), seule donnée
  disponible.

Autre limite, côté saisie : le back valide `startDate` en `@FutureOrPresent` et
`endDate` en `@Future` sur un **instant**. Une date du jour, envoyée à minuit,
serait donc déjà passée et refusée. Le sélecteur de date commence en
conséquence demain. Permettre « la campagne démarre aujourd'hui » demanderait
d'assouplir la validation back pour qu'elle porte sur la journée et non sur
l'instant.

## Vérification

```bash
cd apps/back && mvn -B verify   # 151 tests, BUILD SUCCESS
cd apps/front && npm test       # 35 fichiers, 304 tests
```

La page a par ailleurs été parcourue dans un navigateur (`ng serve`, API
bouchonnée faute de base locale) : chargement, sélection d'une campagne, période
ouverte et campagne sans date, recherche, et création d'une campagne jusqu'à la
notification de succès. Aucune erreur console.

Tests ajoutés :

- `CampaignServiceTest` (5) — la lecture renvoie des DTO publications comprises,
  campagne sans publication, base vide ; la création rattache les publications à
  leur campagne et dérive le statut de la date de début.
- `CampaignControllerTest` (6) — contrat de `GET` (corps, absence de retour vers
  la campagne depuis ses publications, tableau vide) et de `POST` (`201`, `400`
  sur nom vide et sur date de début passée).
- `CampaignListComponent` (9), `CampaignDetailComponent` (7),
  `CreateCampaignModalComponent` (6), `CampaignsPageComponent` (12) et le
  `POST` de `CampaignsService`.

## Reste à faire

- **Rattacher une publication à une campagne** depuis cette page : l'endpoint
  n'existe pas encore côté back (la page Publications relaie déjà son absence).
- **Modifier ou archiver une campagne** : seules la lecture et la création sont
  exposées.
- Les éléments de maquette listés plus haut, si le domaine les adopte.
