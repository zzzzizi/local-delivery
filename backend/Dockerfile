FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Database-backed tests run separately against PostgreSQL, not while building an image.
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S delivery && adduser -S -G delivery delivery
WORKDIR /app
COPY --from=build --chown=delivery:delivery /build/target/local-delivery-0.1.0.jar app.jar
USER delivery
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
