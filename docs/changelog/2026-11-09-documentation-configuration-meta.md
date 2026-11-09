# Documenter la configuration de l'app Meta

## Contexte

La connexion du canal Facebook depuis l'interface a été validée en préproduction
le 2026-11-09 : URI de redirection déclarée dans l'app Meta, connexion de la Page
et contrôle du jeton réussis. La configuration côté Meta n'était décrite qu'en
quelques lignes dans le runbook de préproduction. Il faudra la refaire pour la
production.

## Changements

- **Nouveau guide [`docs/configuration-meta.md`](../configuration-meta.md)** :
  - les valeurs à obtenir et l'endroit où elles vont (Variables et Secret de
    l'Environment GitHub) ;
  - la création de l'app, ses paramètres de base, l'URI de redirection exacte
    (construite à partir de `FRONTEND_URL`) ;
  - les trois permissions demandées et leur rôle, les rôles nécessaires dans
    l'app ;
  - le mode Live et ce qui reste à confirmer pour la production (App Review,
    vérification de l'entreprise) ;
  - un tableau de dépannage reprenant les messages d'AgoraPilot.
- Runbook de préproduction : renvoi vers ce guide depuis « Prérequis côté Meta ».
- `docs/README.md` : guide ajouté à la structure.
- Roadmap : « valider la connexion en préproduction » coché.

## Vérification

Documentation seule. Les permissions, le chemin de retour et les messages cités
ont été relus dans `FacebookConnectionService`, et `FRONTEND_URL` dans
`docker-compose.preprod.yaml`. Les liens relatifs ont été vérifiés.

## Commit

_voir PR_
