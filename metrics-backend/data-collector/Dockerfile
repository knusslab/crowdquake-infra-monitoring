# 1단계: 빌드용 이미지 (Gradle + JDK 21)
FROM gradle:8.5.0-jdk21 AS build
WORKDIR /build

# Gradle 캐시 최적화: 의존성 관련 파일만 먼저 복사
COPY metrics-backend/data-collector/build.gradle metrics-backend/data-collector/
COPY metrics-backend/build.gradle metrics-backend/
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew .
COPY gradle/wrapper gradle/wrapper


# 전체 소스 복사
COPY . .

# container-data-collector 모듈만 빌드
RUN ./gradlew :metrics-backend:data-collector:clean :metrics-backend:data-collector:build -x test

RUN find /build -name "*.jar"

# 2단계: 실행용 이미지 (JRE 21)
FROM eclipse-temurin:21-jre
WORKDIR /app

# 비root 사용자 생성 및 권한 부여
RUN adduser --disabled-password --gecos '' appuser \
  && chown -R appuser:appuser /app

# 빌드된 JAR 복사
COPY --from=build /build/metrics-backend/data-collector/build/libs/data-collector-0.0.1-SNAPSHOT.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]

