# syntax=docker/dockerfile:1
#
# HiLITE Mall Smart Parking - container image.
#
#   docker build -t hilite-parking .
#   docker run -p 8080:8080 -v hilite-data:/data hilite-parking
#   (or: docker compose up --build)
#
# Tests are NOT run in the image build (-DskipTests): they run locally / in CI with
# `mvn verify` before an image is built. The image only packages already-tested code.
#
# SQLite: run exactly ONE container per database volume. SQLite is a single-file database;
# never scale this service horizontally or share /data between replicas.

############################
# 1) Build stage
############################
# Official Maven image on Eclipse Temurin 21 JDK (Ubuntu 24.04 "noble").
# Maven 3.9.x line kept to match the version the project is developed with.
FROM maven:3.9.16-eclipse-temurin-21-noble AS build

# Node.js: deliberately NOT installed here. vaadin-maven-plugin (build-frontend) looks for a
# suitable Node.js (Vaadin 25.3 needs Node 24+) and, when none is on the PATH, downloads the
# exact Node version Vaadin is tested with into ~/.vaadin. That keeps the Node version owned by
# the Vaadin release instead of by this file, and needs nothing beyond the network access
# Maven already requires. The cache mounts below keep ~/.m2 and ~/.vaadin between builds.
WORKDIR /build

# pom.xml plus any frontend files Vaadin may have generated at the project root
# (package.json, package-lock.json, vite.config.ts, tsconfig.json, types.d.ts). The globs may
# match nothing; pom.xml always matches, which keeps the COPY valid.
COPY pom.xml package*.json vite.config.ts* tsconfig.json* types.d.ts* ./
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    mvn -B -DskipTests package \
 && test -f target/hilite-parking.jar

############################
# 2) Runtime stage
############################
# JRE only (no compiler, no Maven, no Node). Pinned to an exact Temurin release on Ubuntu 24.04
# so rebuilds are reproducible; bump the tag deliberately for Java security updates.
# The Adoptium noble image installs curl and wget (see adoptium/containers 21/jre/ubuntu/noble
# Dockerfile), which the HEALTHCHECK below uses.
FROM eclipse-temurin:21.0.12.1_1-jre-noble

# Unprivileged user with a fixed, high UID/GID (independent of the image's own "ubuntu" user).
RUN groupadd --system --gid 10001 hilite \
 && useradd --system --uid 10001 --gid hilite --home-dir /app --no-create-home \
        --shell /usr/sbin/nologin hilite \
 && mkdir -p /app /data \
 && chown hilite:hilite /data \
 && chmod 0750 /data

WORKDIR /app
COPY --from=build --chown=root:root /build/target/hilite-parking.jar /app/hilite-parking.jar

# Runtime configuration (override with -e / --env-file; see .env.example).
ENV PORT=8080 \
    HILITE_DB_PATH=/data/hilite-parking.db \
    HILITE_TIME_ZONE=Asia/Kolkata \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# /data is created and chowned BEFORE the VOLUME instruction, so a new named volume is
# initialised with hilite:hilite ownership. (A host bind mount keeps the host's permissions:
# make that directory writable for UID 10001.)
VOLUME ["/data"]

USER hilite:hilite
EXPOSE 8080

# Spring Boot Actuator health (includes the SQLite check). curl ships in the base image.
# start-period covers JVM + Vaadin startup on small hosts.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/hilite-parking.jar"]
