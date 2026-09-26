# 2026-08-15 — GitHub CLI et règle des Pull Requests en draft

## Contexte

`CLAUDE.md` impose depuis le 2026-07-20 d'ouvrir une Pull Request en draft vers `develop`
après chaque push, mais la règle n'était pas outillée : `gh` (GitHub CLI) était absent du
poste, et le repli consistait à ouvrir à la main une URL `compare/develop...<branche>`
pré-remplie, puis à penser à cliquer « Create draft pull request ». Étape manuelle, donc
oubliable — la PR #33 avait ainsi été ouverte hors draft.

`gh` v2.97.0 est désormais installé (`brew install gh`) et authentifié sur le compte
`lpzkjdoi` avec le scope `repo`. La règle peut être appliquée sans intervention manuelle.

## Changements

- `CLAUDE.md` : la règle PR est explicitée et rendue sans exception — toute PR ouverte par
  l'assistant est en draft, la commande `gh pr create --draft --base develop` est donnée, et
  le passage en « ready for review » est réservé à Maxime.
- `docs/gitflow.md` :
  - le cycle d'une modification intègre l'ouverture de la PR draft comme étape à part
    entière, et distingue le passage en « ready for review » du merge ;
  - nouvelle section **Pull Requests en draft** : motivation de la règle et commandes
    `gh pr create --draft` / `gh pr ready [--undo]` ;
  - nouvelle section **GitHub CLI (`gh`)** dans les recommandations d'outillage :
    installation, authentification interactive à faire une fois par poste, et tableau des
    commandes courantes (PR, suivi de CI).
- PR #33 (`claude/notification-service` → `develop`) repassée en draft.
- Ajout de la présente entrée de changelog et mise à jour de `docs/README.md`.

## Vérification

- `gh auth status` → `Logged in to github.com account lpzkjdoi`, protocole SSH, scopes
  `admin:public_key, gist, read:org, repo`.
- `gh pr ready --undo 33` puis `gh pr view 33 --json isDraft` → `"isDraft": true`,
  base `develop`, état `OPEN` : accès en lecture et en écriture au dépôt confirmé.
- Aucun code applicatif modifié : documentation et conventions uniquement.
