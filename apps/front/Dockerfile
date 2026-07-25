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

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# Le builder `application` d'Angular écrit le bundle navigateur dans
# <outputPath>/browser, soit dist/front/browser (outputPath = dist/front).
COPY --from=build /build/dist/front/browser /usr/share/nginx/html

EXPOSE 80
