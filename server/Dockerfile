# Build from the repository root:
#   docker build -f server/Dockerfile -t ddr-leaderboard .
#
# That builds the server from source inside Docker (what docker compose
# does). CI instead builds the jar once in its Gradle job, where downloads
# are cached, and packages that jar with --target prebuilt.

FROM gradle:8.9-jdk17 AS build
WORKDIR /src
# Server-only build: settings.gradle.kts skips the Android app.
ENV DDR_SERVER_ONLY=1
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN gradle --no-daemon :server:buildFatJar

FROM eclipse-temurin:17-jre AS runtime
RUN useradd --system --uid 10001 ddr
WORKDIR /app
USER ddr
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/ddr-leaderboard.jar"]

# A jar built outside Docker (./gradlew :server:buildFatJar). Docker only
# builds the stages a target needs, so this one never runs Gradle.
FROM runtime AS prebuilt
COPY server/build/libs/ddr-leaderboard.jar ./ddr-leaderboard.jar

# The default (last) stage: built from source above.
FROM runtime AS server
COPY --from=build /src/server/build/libs/ddr-leaderboard.jar ./ddr-leaderboard.jar
