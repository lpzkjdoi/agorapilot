# Traçabilité : auteurs et journal des actions

## Contexte

C'est l'étape 5, et la dernière, de la
[proposition sur les comptes et les droits](../proposals/2026-10-24-comptes-et-droits.md).
Les comptes existaient, mais rien ne disait qui avait écrit une publication,
programmé une diffusion, ou annulé une diffusion qui disparaissait alors sans
trace.

## Changements

### Back

- **Migration `V4__tracabilite.sql`** :
  - colonnes `publications.created_by` et `updated_by`, et
    `publication_occurrences.created_by` (pseudos recopiés) ;
  - contrainte des types du journal élargie. C'est le premier changement d'enum
    passé par Flyway plutôt qu'à la main.
- **Audit Spring Data** (`@CreatedBy` / `@LastModifiedBy`) nourri par
  `CurrentUser`, le pseudo du compte de la requête. Hors requête (ordonnanceur,
  démarrage), il n'y a pas d'auteur.
- **Une diffusion n'a qu'un auteur, pas de « dernier modificateur »** : la
  répartition automatique déplace les diffusions des autres, et la lui attribuer
  tromperait.
- **Les modifications de publication sont écrites aussitôt** (`saveAndFlush`).
  Sans cela, la réponse montrait encore l'ancien modificateur, l'audit n'étant
  posé qu'à l'écriture.
- **Le journal accueille les actions qui ne laissent pas de ligne** :
  - diffusion déplacée (avant → après), reprise, annulée (avec qui l'avait
    programmée) ;
  - publication supprimée (début du texte, archivée ou non) ;
  - canal suspendu (par l'ordonnanceur, sans acteur) et canal repris.

  Les dates y sont lisibles telles quelles (`26/10/2026 18:30`).
- **DTO** : `PublicationDTO` expose `createdAt`, `createdBy`, `updatedAt`,
  `updatedBy`, et `PublicationOccurrenceDTO` expose `createdBy`.

### Front

- **Carte de publication** : « 24 octobre 2026 • 21:04 par camille • modifiée
  par bruno ». Si l'auteur est aussi le dernier modificateur, seul « modifiée »
  s'affiche.
- **Bulle du calendrier** : « programmée par camille ».
- **Journal de la page Comptes** :
  - libellés des six nouveaux événements ;
  - « système » pour une action sans acteur ;
  - suspensions en rouge ;
  - une phrase qui dit ce que le journal contient.

## Vérification

- `mvn -B test` : 463 tests, dont `TraceabilityFlowTest` (6), avec deux vraies
  sessions :
  - auteur et dernier modificateur d'une publication ;
  - auteur d'une diffusion inchangé quand une autre la déplace ;
  - journal d'un déplacement, d'une annulation, d'une reprise et d'une
    suppression, avec leur acteur ;
  - suspension sans acteur, puis reprise avec acteur.
- `npm test` : 564 tests ; `ng lint` et `ng build` sans avertissement.
- De bout en bout, avec le vrai back sur PostgreSQL local et deux comptes :
  - publication créée par l'un, modifiée par l'autre (réponse et carte) ;
  - diffusion déplacée puis annulée, visibles au journal de la page Comptes.

  Les publications antérieures s'affichent sans auteur, comme prévu.
