# 1단계: 빌드용 이미지 (Gradle + JDK 21)
FROM gradle:8.5.0-jdk21 AS build
WORKDIR /build

# Gradle 캐시 최적화: 의존성 먼저 복사
COPY metrics-backend/consumer/build.gradle metrics-backend/consumer/
COPY metrics-backend/build.gradle metrics-backend/
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew .
COPY gradle/wrapper gradle/wrapper


# 전체 소스 복사
COPY . .

# consumer 모듈만 빌드
RUN ./gradlew :metrics-backend:consumer:build -x test

RUN find /build -name "*.jar"

# 2단계: 실행용 이미지 (JRE 21)
FROM eclipse-temurin:21-jre
WORKDIR /app

# 빌드된 JAR 복사
COPY --from=build /build/metrics-backend/consumer/build/libs/consumer-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

