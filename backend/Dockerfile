# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------
# 1단계: Gradle Wrapper로 Spring Boot 실행 JAR 빌드
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# 빌드 설정과 Wrapper를 먼저 복사해 Docker 레이어 캐시를 활용한다.
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle

RUN chmod +x gradlew

# 애플리케이션 소스 복사 후 실행 가능한 JAR 생성
COPY src src
RUN ./gradlew --no-daemon clean bootJar \
    && JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/app.jar

# ---------------------------------------------------------------------
# 2단계: 실행에 필요한 JRE와 JAR만 포함
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

RUN mkdir -p /app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
