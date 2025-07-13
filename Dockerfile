# Étape 1 : Build avec Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Image finale
FROM eclipse-temurin:17-jdk
WORKDIR /app

# ✅ Installer curl + kubectl proprement
# Étape 7 : Installer kubectl sans curl compliqué
RUN apt-get update && \
    apt-get install -y apt-transport-https ca-certificates curl gnupg && \
    curl -fsSLo /usr/share/keyrings/kubernetes-archive-keyring.gpg https://packages.cloud.google.com/apt/doc/apt-key.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/kubernetes-archive-keyring.gpg] https://apt.kubernetes.io/ kubernetes-xenial main" > /etc/apt/sources.list.d/kubernetes.list && \
    apt-get update && \
    apt-get install -y kubectl

COPY --from=build /app/target/*.jar app.jar

# Profil K8s si besoin
ENV SPRING_PROFILES_ACTIVE=k8s

# Démarrage
ENTRYPOINT ["java", "-jar", "app.jar"]
