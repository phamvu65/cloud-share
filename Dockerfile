FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Headless LibreOffice powers the Word/PPTX/Excel/HTML <-> PDF conversion jobs.
# Alpine's musl libc is unreliable with LibreOffice's UNO/JVM bridge, hence the
# switch from the previous eclipse-temurin:17-jdk-alpine base to this Ubuntu-based one.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice fonts-dejavu fonts-liberation2 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]