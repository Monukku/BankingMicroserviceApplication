FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn -q -DskipTests package

FROM eclipse-temurin:21-jdk-alpine AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S rewabank && adduser -S loans -G rewabank
USER loans

COPY --from=layers /app/dependencies/          ./
COPY --from=layers /app/spring-boot-loader/    ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/           ./

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]