# Vérification des publications et actions selon les droits

## Contexte

C'est l'étape 4 de la
[proposition sur les comptes et les droits](../proposals/2026-10-24-comptes-et-droits.md).
Jusqu'ici :

- un brouillon pouvait être programmé et diffusé ;
- n'importe quel compte qui pouvait écrire pouvait aussi marquer une
  publication comme vérifiée ;
- le front proposait toutes les actions à tout le monde, et un refus ne se
  découvrait qu'au clic.

## Changements

### Back

- **Seule une publication vérifiée part** : programmer, reprendre un échec ou
  diffuser immédiatement un brouillon répond `409`.
- **L'ordonnanceur revérifie au moment de diffuser.** Une diffusion programmée
  dont la publication est repassée en brouillon entre-temps passe en échec, avec
  le motif « la publication n'est plus vérifiée ».
- **Le droit `publications:verify`** est exigé pour :
  - créer une publication vérifiée ;
  - changer le statut d'une publication, dans un sens ou dans l'autre (`403`
    sinon).
- **Sans ce droit, modifier une publication vérifiée la repasse en brouillon**,
  que ce soit son texte ou ses visuels : ce qui a été vérifié, c'est l'ancienne
  version. Un enregistrement sans changement la laisse vérifiée.
- **La création de campagne n'accepte plus de publications imbriquées.** Elle
  permettait de créer des publications vérifiées avec le seul droit
  `campaigns:write`, et le front ne s'en servait pas.

### Front

- **Bandeau** : une entrée par page que le compte a le droit de voir.
- **Pages** : chacune exige son droit de lecture. Sans lui, la garde mène à la
  première page permise (`homePath`) ; sans aucun droit, à la page « Aucun
  droit », qui dit à qui s'adresser.
- **Publications** :
  - création, modification, suppression, diffusion, programmation, visuels et
    campagne ne sont proposés qu'avec leurs droits ;
  - « Publier sur Facebook » et « Programmer » sont verrouillés sur un brouillon,
    avec la raison au survol ;
  - la case « vérifiée » n'apparaît qu'avec `publications:verify` ;
  - sans ce droit, modifier une publication vérifiée affiche un avertissement, et
    la notification signale le retour en brouillon ;
  - les campagnes du filtre ne sont chargées qu'avec `campaigns:read`.
- **Calendrier** : sans `occurrences:schedule`, ni « Modifier » ni « Reprendre »,
  et aucune pastille ne se déplace.
- **Campagnes** : pas de bouton de création sans `campaigns:write`.
- **Médiathèque** : pas de zone de dépôt ni d'actions sans `medias:write` ; pas
  de suppression sans `medias:delete`.
- **Bandeau des suspensions** :
  - « Reprendre les diffusions » n'apparaît qu'avec `channels:manage` ;
  - le bandeau n'interroge le back qu'avec `publications:read`.
- **Tests** : utilitaire `core/auth/auth.testing.ts` (`provideSignedInAs`,
  `signInAs`), qui simule un compte avec des droits choisis.

## Vérification

- `mvn -B test` : 457 tests, dont `PublicationVerificationFlowTest` (10), avec de
  vraies sessions d'un rédacteur et d'un diffuseur :
  - créer ou changer le statut sans le droit ;
  - texte ou visuels modifiés qui repassent en brouillon ;
  - texte inchangé qui reste vérifié ;
  - diffuseur qui garde la vérification ;
  - programmer, diffuser ou reprendre un brouillon refusé ;
  - diffusion programmée d'un brouillon non partie.
- `npm test` : 562 tests, dont un cas « droits limités » pour chaque composant
  modifié ; `ng lint` et `ng build` sans avertissement.
- De bout en bout, avec le vrai back sur PostgreSQL local et un compte rédacteur
  (lecture et écriture, programmation, sans vérification ni diffusion) :
  - bandeau sans « Comptes » ;
  - ni « Publier sur Facebook » ni « Supprimer » ;
  - « Programmer » verrouillé sur le brouillon ;
  - avertissement dans la modale ;
  - publication vérifiée repassée en brouillon après modification, avec sa
    notification ;
  - `/comptes` redirigé vers le tableau de bord.

## Reste à faire

L'étape 5 de la proposition : la traçabilité (auteurs des publications, des
diffusions et des levées de suspension).
