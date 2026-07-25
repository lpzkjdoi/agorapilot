# Image de production du backend AgoraPilot.
# Contexte de build attendu : apps/back

# --- Étape 1 : compilation ------------------------------------------------
# Le pom est copié seul dans un premier temps : tant que les dépendances ne
# changent pas, la couche `go-offline` reste en cache et les builds suivants
# ne retéléchargent rien.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Les tests sont exécutés par la CI (`mvn -B verify`), pas au moment de la
# construction de l'image.
RUN mvn -B -DskipTests package

# --- Étape 2 : exécution --------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# tzdata est requis pour que TZ soit réellement pris en compte sur Alpine :
# FacebookTokenScheduler tourne en cron "0 0 3 * * *" sans zone explicite,
# donc l'heure du conteneur détermine l'heure de renouvellement du token.
RUN apk add --no-cache tzdata
ENV TZ=Europe/Paris

RUN addgroup -S agorapilot && adduser -S -G agorapilot agorapilot
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
RUN chown agorapilot:agorapilot app.jar
USER agorapilot

EXPOSE 8080

# MaxRAMPercentage laisse la JVM s'adapter à la limite mémoire du conteneur
# plutôt qu'à celle de l'hôte.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
