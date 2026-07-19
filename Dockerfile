# Single production image: builds the React app, bakes it into the Spring Boot jar, runs one service.
# Serves the UI and the API on one origin/port, so there's one URL and one password to manage.

# --- 1. build the frontend (Node 20, independent of your machine's Node) ---
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build     # -> /fe/dist

# --- 2. build the backend, embedding the frontend as static resources (JDK 17) ---
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# --- 3. runtime: slim JRE ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
