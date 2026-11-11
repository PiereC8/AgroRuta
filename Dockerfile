# Imagen del backend para Render. Compila con el Maven Wrapper del proyecto y corre el .jar con Java 21.
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home agroruta
COPY --from=construccion /app/target/*.jar app.jar
USER agroruta
# El plan gratis de Render da 512 MB: la memoria de Java se ajusta a ese límite.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
