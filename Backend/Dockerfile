FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/sbp-*.jar app.jar

USER spring
EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
