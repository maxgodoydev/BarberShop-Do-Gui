FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY app/pom.xml app/pom.xml
COPY app/src app/src
RUN mvn -f app/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/app/target/barbershop-do-gui.jar app.jar
USER appuser
# Padrões seguros que não dependem de lembrar variáveis no Render (cookie Secure, forward headers). Sobrescreva com a variável de ambiente se precisar.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
