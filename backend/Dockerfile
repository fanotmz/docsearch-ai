FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system docsearch && useradd --system --gid docsearch docsearch
WORKDIR /app
COPY --from=build /app/target/docsearch-ai-0.1.0-SNAPSHOT.jar app.jar
USER docsearch
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "/app/app.jar"]
