FROM eclipse-temurin:21.0.11_10-jdk-alpine AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY app/build.gradle.kts app/
RUN ./gradlew :app:dependencies --no-daemon
COPY app/src app/src
RUN ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:21.0.11_10-jre-alpine
WORKDIR /app
COPY --from=builder /app/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
