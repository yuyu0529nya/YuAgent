FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY YuAgent/pom.xml ./
COPY YuAgent/.mvn .mvn
COPY YuAgent/mvnw ./

RUN mvn dependency:go-offline -B

COPY YuAgent/src ./src
COPY YuAgent/eclipse-formatter.xml ./eclipse-formatter.xml

RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -g 1000 yuagent && adduser -D -s /bin/sh -u 1000 -G yuagent yuagent

WORKDIR /app

COPY --from=builder /app/target/*.jar ./app.jar

RUN mkdir -p logs storage && chown -R yuagent:yuagent /app

USER yuagent

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8088/api/health || exit 1

EXPOSE 8088

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
