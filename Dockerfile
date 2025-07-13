# Étape 1 : Build avec Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Image finale avec Java + kubectl
FROM eclipse-temurin:17-jdk
WORKDIR /app

# ⬇️ Installe kubectl dans l'image finale
RUN apt-get update && apt-get install -y curl && \
    curl -s https://dl.k8s.io/release/stable.txt -o /tmp/version.txt && \
    curl -LO "https://dl.k8s.io/release/$(cat /tmp/version.txt)/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/bin/


# Copie du jar compilé depuis le build
COPY --from=build /app/target/*.jar app.jar

# Profil Spring (ex: application-k8s.yml)
ENV SPRING_PROFILES_ACTIVE=k8s

# Commande de démarrage
ENTRYPOINT ["java", "-jar", "app.jar"]
