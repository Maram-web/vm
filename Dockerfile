# Étape 1 : Build avec Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Image finale
FROM eclipse-temurin:17-jdk
WORKDIR /app

# ✅ Installer curl + kubectl proprement
RUN apt-get update && apt-get install -y curl && \
    KUBECTL_VERSION=$(curl -s https://dl.k8s.io/release/stable.txt) && \
    curl -LO https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl && \
    chmod +x kubectl && mv kubectl /usr/bin/kubectl

# Copier le jar
COPY --from=build /app/target/*.jar app.jar

# Profil K8s si besoin
ENV SPRING_PROFILES_ACTIVE=k8s

# Démarrage
ENTRYPOINT ["java", "-jar", "app.jar"]
