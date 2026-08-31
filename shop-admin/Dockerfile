FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY shop-common/pom.xml shop-common/pom.xml
COPY shop-identity/pom.xml shop-identity/pom.xml
COPY shop-catalog/pom.xml shop-catalog/pom.xml
COPY shop-trade/pom.xml shop-trade/pom.xml
COPY shop-inventory/pom.xml shop-inventory/pom.xml
COPY shop-marketing/pom.xml shop-marketing/pom.xml
COPY shop-payment/pom.xml shop-payment/pom.xml
COPY shop-content/pom.xml shop-content/pom.xml
COPY shop-reporting/pom.xml shop-reporting/pom.xml
COPY shop-integration/pom.xml shop-integration/pom.xml
COPY shop-boot/pom.xml shop-boot/pom.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -DskipTests package -pl shop-boot -am \
    && cp shop-boot/target/shop-boot-*.jar /tmp/shop-boot.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
COPY --from=build /tmp/shop-boot.jar /app/shop-boot.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/shop-boot.jar"]
