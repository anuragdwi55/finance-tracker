# -------- Build stage --------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# -------- Runtime stage --------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

# Render provides $PORT dynamically; pass it to Spring.
ENV JAVA_OPTS="-Dserver.port=${PORT:-8080}"
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]