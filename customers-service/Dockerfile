FROM eclipse-temurin:21-jdk AS layers
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r rewabank && useradd -r -g rewabank app
USER app

COPY --from=layers /app/dependencies/          ./
COPY --from=layers /app/spring-boot-loader/    ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/           ./

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
