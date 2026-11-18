# Image de production du frontend AgoraPilot.
# Contexte de build attendu : apps/front

# --- Étape 1 : build Angular ----------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# Les manifestes sont copiés seuls d'abord : la couche `npm ci` reste en cache
# tant que les dépendances ne bougent pas.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Étape 2 : service statique -------------------------------------------
FROM nginx:1.27-alpine

# Conf nginx propre à l'environnement : `docker/nginx.conf` (préproduction, avec
# le proxy Swagger) par défaut, `docker/nginx.prod.conf` pour la production.
ARG NGINX_CONF=docker/nginx.conf
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
COPY docker/headers /etc/nginx/headers

# Le builder `application` d'Angular écrit le bundle navigateur dans
# <outputPath>/browser, soit dist/front/browser (outputPath = dist/front).
COPY --from=build /build/dist/front/browser /usr/share/nginx/html

EXPOSE 80
