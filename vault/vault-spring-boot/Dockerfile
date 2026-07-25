# Multi-stage build: compile with Maven+JDK, run with just a JRE so the
# final image doesn't carry the whole build toolchain.
FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /build
COPY pom.xml .
# Downloads dependencies into a Docker layer that's cached as long as
# pom.xml doesn't change, so editing Java source doesn't re-download the
# internet on every rebuild.
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/vault-demo.jar app.jar
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
