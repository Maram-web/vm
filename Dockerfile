FROM maven:3.9.6-eclipse-temurin-17 as build
WORKDIR /app
COPY src/main/java/tn/esprit/vmservice .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=k8s
ENTRYPOINT ["java", "-jar", "app.jar"]
