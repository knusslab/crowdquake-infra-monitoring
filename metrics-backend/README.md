﻿# metrics-backend

`metrics-backend`는 컨테이너 및 호스트 머신에서 메트릭 데이터를 수집하고, Kafka를 통해 전송 및 처리하는 백엔드 시스템입니다. 이 프로젝트는 메트릭 수집 → Kafka 전송 → 데이터 수집 및 전송의 흐름을 중심으로 구성되며, Docker 환경에서 실행됩니다.

## 📁 모듈 구성

- **container-data-collector**  
  컨테이너 머신의 자원 사용 데이터를 수집합니다.

- **machine-data-collector**  
  호스트 머신의 자원 사용 데이터를 수집합니다.

- **producer**  
  수집된 데이터를 Kafka로 전송하는 Kafka 프로듀서 역할을 합니다.

- **consumer**  
  Kafka로부터 메트릭 데이터를 수신하며, 내부적으로 WebClient나 WebSocket을 활용해 데이터를 외부에 전송하는 기능도 수행합니다.


---

## 실행방식

- server-monitoring의 README.md를 참고하세요.

---

## 주요 기술 스택 및 의존성

### 공통 기술 스택

- Java 21
- Spring Boot 3.4.4
- Spring Data JPA & JDBC
- Spring Kafka
- Jackson (JavaTime 지원)
- Lombok
- JUnit

### 서비스별 주요 의존성

1. Consumer
- Spring Boot Web, WebFlux
- Spring Kafka
- Jackson (JavaTime 지원)
- Spring Boot Devtools (개발용)

2. Container-data-collector
- Spring Boot Web
- Jackson Databind
- Spring Kafka
- OSHI (시스템 정보 수집)
- Gson (JSON 직렬화)
- H2 Database (테스트/임베디드)
- Spring Boot Devtools (개발용)
- metrics-backend:producer 모듈 의존

3. Producer
- Spring Boot Web
- Spring Kafka
- Gson (JSON 직렬화)
- Spring Boot Devtools (개발용)

---


## 주의사항

- 반드시 프로젝트의 최상위 위치에 `.env` 파일을 직접 생성 후 실행하세요.
- 민감 정보는 외부에 노출되지 않도록 주의하세요.

---


## 문의 및 기여

- 이 프로젝트에 대한 문의, 개선 제안, 버그 제보는 이슈 또는 PR로 남겨주세요.
