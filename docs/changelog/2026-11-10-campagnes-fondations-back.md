# Campagnes : fondations back (modifier, annuler, archiver, statut à jour)

**Date** : 2026-11-10
**Périmètre** : `apps/back` (principalement), `apps/front` (date minimale de création)

## Contexte

La page Campagnes ne permet que de créer une campagne et de la consulter. Pour
en faire un véritable outil de gestion (modifier, supprimer, voir son
calendrier), le back n'exposait que la lecture, la création et la clôture.
C'est le premier lot d'une refonte en quatre étapes :

1. **fondations back** (ce lot) ;
2. actions de gestion dans la page (modifier, clôturer, annuler, supprimer) ;
3. publications de la campagne enrichies de leur planification ;
4. calendrier de la campagne.

## Changements

### Le statut est dérivé à la lecture

Le statut stocké était calculé une fois, à la création : une campagne
« Programmée » le restait indéfiniment. `Campaign.statusAt(now)` le déduit
désormais des dates — brouillon sans date de début, programmée avant elle, en
cours ensuite, terminée une fois la date de fin passée. Seules la clôture
(`COMPLETED`) et l'annulation (`CANCELED`) sont stockées : ce sont des
décisions, pas des conséquences du calendrier. `CampaignMapper` applique ce
statut à chaque réponse.

### La campagne ne supprime plus ses publications

`Campaign.publications` portait `cascade = ALL, orphanRemoval = true` : la
suppression d'une campagne aurait supprimé ses publications, et retirer une
publication de la liste l'aurait effacée. La relation est maintenant de simple
lecture (`mappedBy` seul) ; le rattachement reste porté par
`Publication.campaign`.

### Nouveaux endpoints

| Méthode | Chemin | Effet | Droit |
|---|---|---|---|
| `GET` | `/api/campaigns/{id}` | une campagne | `campaigns:read` |
| `PUT` | `/api/campaigns/{id}` | remplace nom, description, période | `campaigns:write` |
| `POST` | `/api/campaigns/{id}/cancellation` | annule (`409` si terminée ou déjà annulée) | `campaigns:write` |
| `DELETE` | `/api/campaigns/{id}` | archive, `204` | `campaigns:write` |
| `GET` | `/api/occurrences?from&to&campaignId` | diffusions d'une campagne (paramètre facultatif) | `publications:read` |

- **Modifier** n'impose aucune date future : une campagne en cours garde sa date
  de début passée. Une campagne clôturée ou annulée le reste.
- **Supprimer = archiver** : la campagne disparaît de toutes les lectures (`404`
  ensuite, y compris pour y rattacher une publication) et ses publications sont
  **détachées**, pas supprimées. La ligne reste en base.
- **Annuler** conserve les dates, qui disent ce qui était prévu. Les diffusions
  déjà programmées des publications ne sont **pas** touchées.

### Validation des dates à la journée

`CreateCampaignRequest` contrôlait ses dates sur l'instant : minuit du jour,
envoyé par le front, était déjà « passé ». Les contrôles portent maintenant sur
la journée, et une date de fin antérieure à la date de début est refusée (`400`)
à la création comme à la modification. Côté front, le sélecteur de la modale de
création commence aujourd'hui au lieu de demain.

Une description absente est enregistrée vide : la colonne est `NOT NULL`.

## Vérification

```bash
cd apps/back && mvn -B verify   # 363 tests, BUILD SUCCESS
cd apps/front && npm test       # 62 fichiers, 585 tests
```

Tests ajoutés ou adaptés :

- `CampaignServiceTest` : statut dérivé (7), modification (5), annulation (6),
  archivage (2), description absente.
- `CampaignControllerTest` : `GET /{id}`, `PUT`, annulation, `DELETE`, création
  du jour acceptée, période à l'envers refusée.
- `CampaignPersistenceTest` : l'archivage détache la publication sans la
  supprimer, contre H2.
- `OccurrenceSlotsPersistenceTest` et `PublicationOccurrenceControllerTest` :
  filtre `campaignId`.
- `EndpointPermissionsTest` : droits des quatre nouveaux endpoints.
- Front : `CreateCampaignModalComponent` propose la date du jour.

## Reste à faire

- Lots 2 à 4 (front).
- **Annulation et diffusions** : faut-il déprogrammer les diffusions à venir
  d'une campagne annulée ? Laissé en l'état pour l'instant.
- `CampaignDTO` embarque ses publications complètes ; une liste de campagnes
  fournie pourra justifier un résumé plus léger.
