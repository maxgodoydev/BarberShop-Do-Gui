
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY app/pom.xml app/pom.xml
COPY app/src app/src

RUN mvn -f app/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/app/target/barbershop-do-gui.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
