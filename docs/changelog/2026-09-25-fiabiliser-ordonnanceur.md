# Fiabiliser l'ordonnanceur de diffusion

## Contexte

[L'ordonnanceur](./2026-09-21-diffusion-planifiee-back.md) diffuse les occurrences
dues depuis le 21/09. Avant de construire l'écran de planification, une relecture
a relevé deux défauts et deux risques qui contredisent le principe du projet :
**ne jamais publier deux fois**.

1. **Double publication après un plantage.** Toute la diffusion tenait dans une
   seule transaction : appel à Facebook, livraison marquée `PUBLISHED`, puis
   commit. Un arrêt du back (ou une base perdue) entre l'appel et le commit
   laissait la livraison `PENDING`, et le balayage suivant republiait le post.
2. **Rattrapage aveugle.** Après une panne de plusieurs jours, tout le retard
   partait d'un coup au redémarrage, y compris l'annonce d'un événement passé.
3. **Boucle sur une exception du publisher.** Si le publisher levait une exception
   au lieu de tracer l'échec, la transaction était annulée, l'occurrence restait
   `SCHEDULED` et revenait chaque minute.
4. **Erreur invisible.** `lastError` était effacée à chaque balayage : elle ne
   vivait qu'une minute, et aucun endpoint ne l'exposait.
5. **Calendrier de la semaine amputé.** `GET /api/occurrences/weekly` s'arrêtait
   à minuit du septième jour, qui restait toujours vide.

## Changements

### Diffusion planifiée en trois transactions

`PublicationDeliveryService.publishScheduledOccurrence()` n'a plus de transaction
englobante (`TransactionTemplate`) :

1. **Prise en charge** : chaque livraison `PENDING` passe `IN_PROGRESS`, validé en
   base **avant** l'appel distant. C'est aussi là qu'une livraison est soldée
   `FAILED` sans être tentée : canal sans publisher, retard au-delà du seuil,
   ou diffusion précédente interrompue.
2. **Diffusion** : une transaction par canal. Une exception qui échappe au
   publisher est rattrapée, et la livraison est soldée `FAILED` avec son motif.
3. **Résolution** du statut de l'occurrence.

Nouveau statut de livraison **`IN_PROGRESS`**. Les balayages ne se chevauchent pas
et le back tourne en une seule instance : une livraison encore `IN_PROGRESS` au
début d'un balayage vient donc forcément d'une diffusion interrompue. Elle est
soldée `FAILED` (« Delivery interrupted… check FACEBOOK before publishing
again »), **jamais rejouée**. `refreshStatus()` traite `IN_PROGRESS` comme non
tranchée, ce qui garde l'occurrence dans la file jusqu'à ce qu'elle soit soldée.

### Seuil de retard

`agorapilot.scheduling.max-lateness` (`OCCURRENCES_MAX_LATENESS`, défaut `PT1H`).
Au-delà, la livraison passe `FAILED` avec le motif « Not published: more than 60
minutes late (scheduled at …) ». Les réglages de planification sont regroupés dans
`SchedulingProperties`.

### Observabilité

- `PublicationOccurrenceScheduler` conserve la dernière erreur (préfixée de
  l'occurrence) et son heure (`lastErrorAt`), jusqu'à l'erreur suivante.
- Nouvel endpoint `GET /admin/scheduler/status` : `lastRunAt`, `lastErrorAt`,
  `lastError`, `occurrencesDelay`, `maxLateness`. Il n'est pas authentifié et
  reste hors du proxy nginx, comme `/admin/facebook`.

### Calendrier de la semaine

L'intervalle est désormais semi-ouvert `[aujourd'hui 00:00, J+7 00:00)`
(`findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan`), et le
regroupement se fait par date complète, non plus par numéro de jour du mois.

### Front

Le type `DeliveryStatus` admet `IN_PROGRESS`, et `occurrence.model.ts` le
réutilise au lieu de redéclarer l'union. Aucun composant n'affiche encore le
statut d'une livraison.

### Documentation

- Runbook : migration de la contrainte `CHECK` de `publication_deliveries.status`,
  et section sur l'état de l'ordonnanceur et les motifs d'échec.
- README : déclenchement automatique coché ; la ligne CI frontend ne réclame plus
  que le lint.

## ⚠️ Migration à passer sur la préproduction avant le déploiement

`ddl-auto: update` ne met pas à jour la contrainte `CHECK` dérivée de l'enum.
Sans la migration, la prise en charge est rejetée et **aucune diffusion planifiée
ne part**. Le déploiement est automatique à la fusion sur `develop` : il faut
passer la migration juste avant. La commande est dans le
[runbook](../deploiement-preprod.md#migration-ponctuelle--statut-in_progress-des-livraisons) :

```sql
ALTER TABLE publication_deliveries DROP CONSTRAINT IF EXISTS publication_deliveries_status_check;
ALTER TABLE publication_deliveries ADD CONSTRAINT publication_deliveries_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
```

La migration est rétrocompatible : l'ancien code n'écrit jamais `IN_PROGRESS`.
Elle peut donc être passée à l'avance.

## Vérification

```bash
cd apps/back && mvn -B verify   # 194 tests, BUILD SUCCESS
cd apps/front && npm test       # 36 fichiers, 317 tests (Node 24)
```

Tests ajoutés ou repris :

- `PublicationDeliveryServiceTest` : prise en charge validée (commit) avant
  l'appel au publisher, livraison interrompue soldée sans rejeu, seuil de retard
  (dépassé et non dépassé), exception du publisher soldée avec rollback, plus les
  cas existants (pas de rejeu d'un `FAILED` ni d'un `PUBLISHED`, canal sans
  publisher, canaux indépendants, occurrence supprimée).
- `ScheduledDeliveryPersistenceTest` (**nouveau**, H2 + vrai `FacebookPublisher`,
  seul l'appel HTTP est bouchonné) : diffusion de bout en bout jusqu'à
  `PUBLISHED`. La livraison est lue `IN_PROGRESS` **depuis une transaction
  indépendante** au moment de l'appel à Facebook. Les cas refus Facebook,
  livraison interrompue et retard dépassé aboutissent tous à `FAILED`, et le
  chargement paresseux de la publication fonctionne sans transaction englobante.
- `PublicationOccurrenceSchedulerTest` : la dernière erreur et son heure survivent
  à un balayage sans erreur.
- `PublicationOccurrenceServiceTest` : `IN_PROGRESS` garde l'occurrence
  `SCHEDULED` ; le calendrier interroge sept jours pleins et range une occurrence
  du septième jour sous ce jour.
- `SchedulerAdminControllerTest` (**nouveau**) : contrat de
  `GET /admin/scheduler/status`.

## Reste à faire

- Écran de planification côté front, et affichage des statuts `FAILED` et
  `IN_PROGRESS`.
- Reprise explicite d'une livraison en échec.
- Flyway : c'est la deuxième migration manuelle de contrainte en une semaine.
