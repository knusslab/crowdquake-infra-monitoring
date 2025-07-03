FROM gradle:8.5.0-jdk21 AS build
WORKDIR /app

# 의존성 캐시 최적화
COPY build.gradle ./
COPY settings.gradle ./
COPY gradle gradle
COPY gradlew .
COPY gradle/wrapper gradle/wrapper


COPY . .
RUN ./gradlew clean build -x test

RUN find /app -name "*.jar"


FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl

COPY --from=build /app/api-backend/build/libs/api-backend-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
