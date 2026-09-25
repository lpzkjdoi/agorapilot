# Écran de programmation : bouton « Programmer » et page Calendrier

## Contexte

Le back sait programmer une diffusion pour un jour et la placer dans la fenêtre
de publication
([créneaux de diffusion](./2026-09-25-creneaux-diffusion-back.md)), mais aucun
écran ne l'exposait. Décisions prises avec Maxime :

- on programme depuis la carte d'une publication, en choisissant un jour ;
- on déplace une diffusion par glisser-déposer dans un calendrier ;
- on peut aussi fixer l'heure.

La maquette Figma prévoit une page `/calendar` (grille du mois, pastilles par
canal, popover au clic sur un jour), consultée pour cette tâche. Elle ne prévoit
en revanche **ni formulaire de programmation, ni état d'échec, ni édition** :
ces éléments ont été conçus hors maquette, avec les briques existantes (modales,
badges).

## Changements

### Programmer depuis la carte

- Nouveau bouton **« Programmer »** sur la carte de publication.
- Modale `ScheduleOccurrenceModalComponent` : **jour** obligatoire (à partir
  d'aujourd'hui), **heure facultative**, et les canaux (Facebook coché,
  Intramuros grisé « bientôt »). Sans heure, le back choisit le créneau ; avec
  une heure, elle est fixée.
- La notification annonce l'heure retenue (« Diffusion programmée le samedi
  3 octobre à 18:30 — l'heure s'ajustera si d'autres publications sont
  programmées ce jour-là »). En cas de refus (fenêtre du jour close, heure
  passée), elle relaie le motif du back et la modale reste ouverte.

### Page Calendrier (`/calendrier`)

Entrée « Calendrier » dans la barre de navigation, entre Campagnes et
Publications comme dans la maquette.

- **En-tête** conforme à la maquette : mois en titre, « Planification des
  publications », boutons ‹ Aujourd'hui ›, légende Facebook / Intramuros.
- **Grille du mois**, semaines **du lundi au dimanche** (usage français ; la
  maquette, en anglais, commence le dimanche). Les jours hors du mois sont en
  gris sans numéro, et aujourd'hui est entouré de bleu nuit.
- **Pastilles** : heure, début du contenu, teinte du canal, et un marqueur par
  état (épingle pour une heure fixe, ✓ pour publiée, pastille rouge « ! » pour
  un échec). L'infobulle donne le contenu complet, le statut et le motif d'un
  échec. Au-delà de trois diffusions, la case affiche « +N autres ».
- **Glisser-déposer** : seules les diffusions encore modifiables se font
  glisser, c'est-à-dire programmées, sans canal servi, et à plus de 5 minutes.
  Elles se déposent sur un jour du mois qui n'est pas passé, et la case d'arrivée
  est surlignée. Une heure fixe voyage avec la diffusion ; une heure automatique
  est recalculée dans le jour d'arrivée.
- **Bulle du jour** (le popover de la maquette) : liste compacte des diffusions
  avec les badges canal et statut, l'heure (fixe ou auto) et le motif d'un
  échec. Un bouton **« Modifier »** apparaît sur les diffusions modifiables ;
  une diffusion imminente l'explique. La bulle s'ouvre sous la case, vers le
  haut sur les deux dernières semaines, et vers la gauche en fin de semaine.
- **Modale « Modifier la diffusion »** : changer de jour (l'équivalent clavier
  du glisser-déposer), fixer l'heure ou la vider (retour à l'automatique), et
  **annuler la diffusion**, en deux temps.
- Après chaque modification, **le mois est rechargé** : le back a re-réparti
  les autres diffusions des jours touchés, et la page ne devine pas les
  nouveaux horaires. Une réponse arrivée après un changement de mois est
  ignorée.

### Back

`PublicationDeliveryDTO` expose désormais `errorMessage`, le motif d'échec d'une
livraison, que le calendrier affiche.

### Fichiers

| Fichier | Rôle |
|---|---|
| `features/calendar/calendar.utils.ts` | grille du mois (lundi → dimanche), titres, intervalles |
| `features/calendar/pages/calendar-page/*` | page, chargement, déplacement, annulation |
| `features/calendar/components/calendar-month-grid/*` | grille, glisser-déposer, emplacement de la bulle |
| `features/calendar/components/calendar-occurrence-chip/*` | pastille déplaçable |
| `features/calendar/components/calendar-day-panel/*` | bulle du jour |
| `features/calendar/components/edit-occurrence-modal/*` | modale de modification |
| `features/occurrences/components/schedule-occurrence-modal/*` | modale « Programmer » |
| `features/occurrences/occurrence.utils.ts` | règles partagées (modifiable, jour, heure, motif) |
| `features/occurrences/occurrences.service.ts` | période, programmation, déplacement, annulation |
| `features/publications/…/publication-card`, `publications-page` | bouton et branchement |
| `core/layout/navbar`, `app.routes.ts` | entrée et route `/calendrier` |
| `styles.css` | badges d'une diffusion (canal, statut), partagés |

## Choix

- **Bulle compacte et modale d'édition.** Une première version plaçait les
  champs d'édition dans la bulle. Vérifiée dans le navigateur, elle masquait les
  pastilles du jour et débordait de la page sur la dernière semaine. La bulle
  est revenue à la liste de la maquette, et l'édition est passée dans une
  modale, comme les autres formulaires de l'application.
- **Glisser-déposer natif (HTML5)**, sans dépendance ajoutée (`@angular/cdk`
  n'est pas installé). La modale sert d'alternative au clavier.
- **Accessibilité** : le clic n'importe où dans une case est un raccourci à la
  souris. Au clavier, on passe par le bouton du numéro du jour. La case est
  focalisable hors tabulation (`tabindex="-1"`) pour satisfaire le lint, qui est
  bloquant en CI, sans exception ajoutée.

## Vérification

```bash
cd apps/back && mvn -B verify          # 235 tests, BUILD SUCCESS
cd apps/front && npm run lint          # aucun problème
cd apps/front && npm test              # 44 fichiers, 407 tests
cd apps/front && npm run build         # sans avertissement de budget
```

**Vérifié dans un vrai navigateur**, avec un front `ng serve` et une API
bouchonnée sur le port 8080 (Docker n'est pas lancé sur le poste) :

- rendu du mois : pastilles publiée, en échec, à heure fixe, « +2 autres » ;
- bulle ouverte vers le haut sur la dernière semaine ;
- modale d'édition, avec une heure fixée puis re-répartition du jour ;
- glisser-déposer réel d'un jour à l'autre ;
- refus du back relayé avec son motif ;
- programmation depuis la page Publications, avec l'heure annoncée.

Nouveaux tests :
- utilitaires du calendrier et des occurrences ;
- pastille (teintes, marqueurs, infobulle, `dataTransfer`) ;
- grille (colonnes, jours hors mois, plafond de trois pastilles, sélection à la
  souris et au clavier, orientation de la bulle, dépôt accepté ou refusé) ;
- bulle ;
- modales de programmation et de modification ;
- page (chargement, erreurs, navigation entre mois, réponse périmée ignorée,
  déplacement, heure fixe conservée, refus, modification, annulation) ;
- route et barre de navigation ;
- bouton de la carte et branchement de la page Publications.

## Reste à faire

- **Test en conditions réelles en préprod** : programmer une publication pour
  aujourd'hui et la voir partir sur la page Facebook.
- Afficher les statuts (dont l'échec) dans la liste « prochaines publications »
  du tableau de bord, encore branchée sur des données partielles.
- **Écart minimal** entre une diffusion à heure fixe et les automatiques
  (roadmap).
