# Build from the repository root:
#   docker build -f server/Dockerfile -t ddr-leaderboard .

FROM gradle:8.9-jdk17 AS build
WORKDIR /src
# Server-only build: settings.gradle.kts skips the Android app.
ENV DDR_SERVER_ONLY=1
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN gradle --no-daemon :server:buildFatJar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 ddr
WORKDIR /app
COPY --from=build /src/server/build/libs/ddr-leaderboard.jar ./ddr-leaderboard.jar
USER ddr
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/ddr-leaderboard.jar"]
