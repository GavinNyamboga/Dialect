FROM eclipse-temurin:24-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar
RUN find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} app.jar \;

FROM eclipse-temurin:24-jre

WORKDIR /app

RUN groupadd --system dialect && useradd --system --gid dialect --home-dir /app --shell /usr/sbin/nologin dialect

COPY --from=build /workspace/app.jar app.jar

USER dialect

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
