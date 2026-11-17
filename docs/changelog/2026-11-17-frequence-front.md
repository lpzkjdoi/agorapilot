# Programmation par fréquence : la modale « Répéter » et les séries au calendrier

## Contexte

Le back sait créer des séries de diffusions à partir d'une règle de répétition
(cf. [changelog back](./2026-11-17-frequence-back.md)). Il manquait de quoi les
programmer depuis l'interface, et de quoi les voir et les arrêter.

## Changements

### Modale « Programmer »

- Deux onglets : **Dates précises**, la saisie existante d'une ou plusieurs
  dates, et **Répéter**.
- **Répéter** (`RepeatFieldsComponent`) :
  - jour de départ, et heure facultative (vide : heure automatique) ;
  - préréglages : chaque jour, jours ouvrés, week-ends, chaque semaine, toutes
    les 2 semaines, chaque mois, tous les 3 mois, tous les 6 mois, chaque
    année ;
  - **Personnaliser** : « tous les N » jours, semaines, mois ou ans. Selon la
    fréquence s'ajoutent les jours de la semaine, les mois de l'année, et pour
    le mensuel ou l'annuel les jours du mois (grille 1 à 31) ou « le premier…
    dernier lundi… dimanche » ;
  - **fin** : jamais, à une date (**par défaut la fin de la campagne** de la
    publication, si elle n'est pas passée) ou après N diffusions ;
  - un résumé en français, mis à jour à chaque saisie (« Toutes les
    2 semaines, le lundi et le jeudi, à partir du 23/11/2026, heure
    automatique, 10 fois. »).
- `recurrence.ts` traduit ces choix en `RRULE` pour le back, et en phrase.
- À l'envoi, la page Publications crée la série. Le message reprend le résumé
  et le nombre de diffusions déjà programmées. En cas de refus du back, la
  modale reste ouverte avec le motif.
- Le bloc « Répéter » est un composant à part, pour tenir le budget CSS de la
  modale.

### Calendrier

- **Pastille** : icône ↻ et mention « Fait partie d'une répétition » dans
  l'infobulle.
- **Bulle du jour** :
  - badge « ↻ Répétée » ;
  - bouton **Arrêter la répétition**, avec le droit de programmer, sur une
    diffusion encore programmée ;
  - confirmation sur place (« Arrêter » ou « Garder ») : toutes les diffusions à
    venir de la série sont annulées, puis le mois est rechargé.

### Service

- `OccurrencesService` : `createSeries`, `getSeries` et `stopSeries`.
- Le modèle `Occurrence` reçoit `seriesId` ; ajout de `OccurrenceSeries`.

## Vérification

- `npm test` : 714 tests verts. Nouveaux tests :
  - `recurrence` : préréglages, personnalisation, fin, résumés ;
  - `RepeatFieldsComponent` ;
  - modale : changement d'onglet, envoi, validation, fin de campagne proposée
    ou non ;
  - page Publications : envoi et refus ;
  - bulle du jour, pastille ;
  - page Calendrier : arrêt et refus.
- `ng lint` et `ng build` OK, sans nouvel avertissement de budget.
- Vérifié dans le navigateur intégré, avec une fausse API :
  - onglet « Répéter » et personnalisation hebdomadaire et mensuelle (la grille
    des jours défile dans la modale) ;
  - envoi de la requête et message de succès ;
  - pastilles ↻ et bulle « ↻ Répétée » avec « Arrêter la répétition » dans le
    calendrier.
- **Non vérifié** : le parcours de bout en bout contre le vrai back.

## Commit

_voir PR_
