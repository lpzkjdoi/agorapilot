# Image de production du frontend AgoraPilot.
# Contexte de build attendu : apps/front
#
# Images de base épinglées par digest (tag lisible + digest de l'index
# multi-architecture), comme celles du back : pour monter de version, relever
# le digest du nouveau tag sur Docker Hub et remplacer les deux.

# --- Étape 1 : build Angular ----------------------------------------------
# Node 22, comme la CI front (front-ci.yml). Cette étape ne part pas en
# production : seul le bundle compilé est recopié dans l'image finale.
FROM node:22.23.5-alpine3.24@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a AS build
WORKDIR /build

# Les manifestes sont copiés seuls d'abord : la couche `npm ci` reste en cache
# tant que les dépendances ne bougent pas.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Étape 2 : service statique -------------------------------------------
# Variante `slim` de la branche stable : nginx avec ses modules intégrés
# (gzip, proxy, en-têtes, cache), sans les modules dynamiques de l'image
# complète (njs, geoip, image-filter, xslt) ni envsubst, dont nos confs ne se
# servent pas. Environ 12 Mo décompressés au lieu de 50. Le `wget` du
# healthcheck vient de busybox, toujours présent.
FROM nginx:1.30.7-alpine3.24-slim@sha256:8c950211a641ec5b23f265450517001f6bcca533666120ebe36cb4cff3d00a55

# Conf nginx propre à l'environnement : `docker/nginx.conf` (préproduction, avec
# le proxy Swagger) par défaut, `docker/nginx.prod.conf` pour la production.
ARG NGINX_CONF=docker/nginx.conf
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
COPY docker/headers /etc/nginx/headers

# Le builder `application` d'Angular écrit le bundle navigateur dans
# <outputPath>/browser, soit dist/front/browser (outputPath = dist/front).
COPY --from=build /build/dist/front/browser /usr/share/nginx/html

EXPOSE 80
