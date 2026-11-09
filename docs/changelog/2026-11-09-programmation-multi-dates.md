# Programmer une publication à plusieurs dates

## Contexte

Une campagne à durée indéterminée doit pouvoir republier une publication à des
dates choisies (le 11/11, le 4/12, le 5/02…). Jusqu'ici, la modale
« Programmer » ne créait qu'une diffusion à la fois : il fallait la rouvrir pour
chaque date.

C'est le premier des deux modes de programmation prévus. Le second, la
**répétition par fréquence** (chaque lundi, tous les 3 mois…, calquée sur les
Rappels iOS/macOS), est reporté après la v1.0 et noté dans la roadmap avec les
choix déjà arrêtés.

## Changements

### Back

- **`POST /api/occurrences/batch`** (`occurrences:schedule`) : une publication,
  des canaux, et une liste de dates (`slots`). Chaque date garde l'une des deux
  formes de `POST /api/occurrences` : `date` seule (heure automatique dans la
  fenêtre du jour) ou `scheduledAt` (heure épinglée, jamais dans le passé).
- **Tout ou rien** : une date refusée (jour passé, fenêtre close) annule la
  transaction entière, et le motif nomme le jour fautif.
- Une même date en double est refusée (400). La liste est plafonnée à 60 dates.
- `PublicationOccurrenceService` : `create` et `createAll` partagent désormais
  les mêmes contrôles (publication vérifiée, canaux servis) et la même création
  d'occurrence. `POST /api/occurrences` est conservé tel quel.

### Front

- **Modale « Programmer des diffusions »** : une ligne par date (jour + heure
  facultative), « + Ajouter une date », bouton de retrait sur chaque ligne dès
  qu'il y en a deux. Les doublons sont signalés avant l'envoi.
- Les dates partent dans l'ordre du calendrier, quel que soit l'ordre de saisie.
- Annonce : l'heure retenue pour une date seule, comme avant ; sinon
  « 2 diffusions programmées : 11/11/2026, 05/02/2027 », avec la mention
  d'ajustement dès qu'une heure est automatique. Un refus sur plusieurs dates
  annonce « Aucune diffusion n'a été programmée. » suivi du motif.

## Vérification

- Back : `mvn -B verify` (contrat HTTP du lot, validation de chaque date et des
  doublons, persistance : ordre, répartition d'un jour qui accueille une date
  automatique et une épinglée, annulation du lot entier sur une date refusée,
  table des droits).
- Front : `npm test`, `npm run lint`, build de production.
- Navigateur intégré, API bouchonnée : deux dates saisies dans le désordre,
  envoyées triées, annonce résumée.

## Commit

_voir PR_
