FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Descargar dependencias primero para aprovechar la cache de Docker
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# Compilar la aplicacion
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV JAVA_OPTS=""

# Usuario sin privilegios para ejecutar la app
RUN useradd -r -u 1001 appuser \
    && mkdir -p /app/storage \
    && chown -R appuser:appuser /app

# Copiar el JAR generado por Maven
COPY --from=build /workspace/target/sistematextil-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]