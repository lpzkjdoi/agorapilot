# Image de production du backend AgoraPilot.
# Contexte de build attendu : apps/back
#
# Toutes les images de base sont épinglées par digest (tag lisible + digest de
# l'index multi-architecture) : un build refait demain produit la même image
# qu'aujourd'hui, et une mise à jour de base passe par une PR. Pour monter de
# version : relever le digest du nouveau tag sur Docker Hub (ou
# `docker buildx imagetools inspect <image>:<tag>`) et remplacer les deux.

# --- Étape 1 : compilation ------------------------------------------------
# Le pom est copié seul dans un premier temps : tant que les dépendances ne
# changent pas, la couche `go-offline` reste en cache et les builds suivants
# ne retéléchargent rien.
FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:308cba8b638ed7e4658cea3f8399066219466211c805f6d5728c3c9c7614661b AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Les tests sont exécutés par la CI (`mvn -B verify`), pas au moment de la
# construction de l'image.
RUN mvn -B -DskipTests package

# --- Étape 2 : JRE minimal ------------------------------------------------
# Plutôt que l'image JRE complète de Temurin (~190 Mo décompressés, avec
# polices, locales musl, gnupg, openssl, coreutils…), on assemble avec jlink un
# runtime qui ne contient que les modules dont l'application a besoin. C'est la
# même JVM HotSpot, avec les mêmes performances : seuls les modules inutilisés
# sont absents.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine-3.24@sha256:c07bb82e004a166b5d3d63b84ad70e2e822f228505e2e0c0b8dfdc2890d22f13 AS jre
WORKDIR /jre

COPY --from=build /build/target/*.jar app.jar

# Modules détectés par jdeps dans le code et les dépendances, recalculés à
# chaque build : une nouvelle dépendance qui en exige un autre l'embarque
# d'elle-même. S'y ajoutent ceux que jdeps ne peut pas voir, car chargés
# dynamiquement (ServiceLoader, fournisseurs de sécurité) :
#   - jdk.crypto.ec   : courbes elliptiques de TLS (appels à graph.facebook.com,
#                       webhooks Discord) ; sans lui, la poignée de main échoue
#                       avec la plupart des serveurs ;
#   - jdk.localedata  : noms de mois et de jours en français (récapitulatif
#                       quotidien, export) ; limité à fr et en ci-dessous ;
#   - jdk.management  : métriques système de l'actuator ;
#   - jdk.zipfs       : système de fichiers zip, utilisé par certaines
#                       bibliothèques pour lire leurs ressources.
#
# `--generate-cds-archive` recrée l'archive CDS par défaut (lib/server/*.jsa),
# que l'image JRE de Temurin fournit et qu'un jlink ne produit pas de lui-même :
# les classes du JDK y sont déjà analysées et prêtes à être mappées en mémoire,
# ce qui raccourcit le démarrage. `-Xshare:on` échoue si elle manque : le build
# s'arrête plutôt que de livrer une image sans elle.
RUN set -eux; \
    mkdir unpacked; \
    cd unpacked; \
    jar xf ../app.jar; \
    detected=$(jdeps --ignore-missing-deps -q --recursive --multi-release 21 \
        --print-module-deps --class-path 'BOOT-INF/lib/*' BOOT-INF/classes); \
    echo "Modules détectés par jdeps : $detected"; \
    jlink \
        --add-modules "$detected,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.zipfs" \
        --include-locales=en,fr \
        --strip-debug --no-man-pages --no-header-files \
        --compress=zip-6 \
        --generate-cds-archive \
        --output /jre/runtime; \
    /jre/runtime/bin/java --list-modules; \
    /jre/runtime/bin/java -Xshare:on -version

# --- Étape 3 : exécution --------------------------------------------------
# Alpine nu : ni shell supplémentaire, ni gestionnaire de paquets superflu. La
# même version d'Alpine que l'image JDK qui a produit le runtime (musl).
FROM alpine:3.24.4@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf

# tzdata est requis pour que TZ soit réellement pris en compte sur Alpine : le
# contrôle quotidien du jeton Facebook (cron "0 0 7 * * *") et la fenêtre de
# publication sont exprimés sans zone explicite, à l'heure du conteneur.
# Rien d'autre : ni polices (aucun texte n'est dessiné dans les images), ni
# certificats système (la JVM a son propre magasin, lib/security/cacerts).
RUN apk add --no-cache tzdata

ENV TZ=Europe/Paris \
    # Encodage des noms de fichiers et des sorties : sans locale, Java retombe
    # sur l'ASCII pour `sun.jnu.encoding`. C.UTF-8 est natif dans musl.
    LANG=C.UTF-8 \
    JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:$PATH

COPY --from=jre /jre/runtime /opt/java

RUN addgroup -S agorapilot && adduser -S -G agorapilot agorapilot

# Point de montage du volume des médias. Il doit exister et appartenir à
# l'utilisateur applicatif AVANT le `USER` : Docker recopie les permissions du
# chemin présent dans l'image lorsqu'il monte un volume nommé encore vide. Sans
# ces deux lignes, le volume appartient à root et le back démarre sans droit
# d'écriture sur la médiathèque.
RUN mkdir -p /var/lib/agorapilot/media && chown -R agorapilot:agorapilot /var/lib/agorapilot

WORKDIR /app

COPY --from=build --chown=agorapilot:agorapilot /build/target/*.jar app.jar
USER agorapilot

EXPOSE 8080

# MaxRAMPercentage laisse la JVM s'adapter à la limite mémoire du conteneur
# plutôt qu'à celle de l'hôte. AWT en mode headless : aucun affichage, le
# traitement des images (aplatissement, ré-encodage JPEG) n'en a pas besoin.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
