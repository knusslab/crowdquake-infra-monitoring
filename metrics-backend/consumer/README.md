# Consumer 모듈

## 개요

이 모듈은 Kafka로부터 메트릭 기반 메시지를 **배치 단위로 수신**하고,  
시스템 내 데이터 저장소에 반영하거나,  
실시간 알림이 필요한 경우 **웹소켓을 통해 클라이언트(웹 프론트엔드)로 데이터를 전송**하는 역할을 담당합니다.

**주요 특징**
- 대량 데이터 처리에 최적화된 병렬 처리 구조
- 컨테이너 환경에서 동작
- Kafka 토픽/그룹 등 주요 설정은 환경 변수로 관리
- 외부 API 호출을 통한 고유 머신 ID 변환 및 임계값(Threshold) 동적 관리

---

## 주요 기술 스택 및 의존성

- Java 21
- Spring Boot 3.4.4
- Spring Web (MVC)
- Spring WebSocket
- Spring WebFlux (Reactive Web)
- Spring Kafka
- Lombok
- Jackson (JavaTime 지원)
- JUnit 5

---

## 환경 변수 및 설정

### 필수 환경 변수

`consumer/src/main/resources/properties/envfile.properties` 파일에 아래 항목들을 반드시 정의해야 합니다.

| 변수명                            | 설명                                                                       |
|-----------------------------------|--------------------------------------------------------------------------|
| `BOOTSTRAP_SERVER`                | Kafka 브로커 주소 (컨테이너 환경에서는 `kafka:<포트번호>`(ex. [kafka 클러스터 ip주소]:9094)로 고정) |
| `KAFKA_TOPIC_HOST`                | host 메트릭 메시지용 Kafka 토픽 이름 (producer와 동일하게 설정)                            |
| `KAFKA_TOPIC_CONTAINER`           | container 메트릭 메시지용 Kafka 토픽 이름 (producer와 동일하게 설정)                       |
| `KAFKA_GROUP_ID_STORAGE_GROUP`    | 데이터 저장소용 그룹 ID (코드 사용자가 임의로 지정 가능)                                       |
| `SOCKET_ALLOWED_ADDR`             | 소켓 통신을 허용할 주소 (ex: `http://localhost:3000`)                              |
| `API_BASE_URL`                    | api 통신을 위한 base url (ex: `http://api-backend:8004`)                      |

> ⚠️ **Kafka 토픽 이름은 반드시 producer 모듈의 토픽과 일치시켜야 합니다.**
> ⚠️ 모든 Kafka 토픽이 사전에 생성되어 있어야 하며, 그룹 ID 충돌이 없도록 관리해야 합니다.
> ⚠️ (참고사항) consumer의 포트번호는 '8000'이며, api-backend의 포트번호는 '8004'입니다.

### 환경 파일 생성 방법

1. `consumer/src/main/resources/properties` 폴더를 생성
2. 그 안에 `envfile.properties` 파일을 생성
3. 위 환경 변수들을 모두 입력

---

## 애플리케이션 설정 및 배포

- **컨테이너 환경**에서 동작하도록 설계되어 있습니다.
- consumer 모듈은 metrics-backend 프로젝트의 최상위 폴더에서 도커로 통합 배포됩니다.
- consumer 모듈 개별적으로 실행시키는 방식은 제공하지 않습니다.
- 환경 변수는 반드시 `envfile.properties` 파일로 주입해야 하며,  
  해당 파일이 없으면 정상 동작하지 않습니다.

---

## 핵심 기능 및 구조

### 1. Kafka 메시지 수신 및 처리

- JSON 형식의 메트릭 데이터를 Kafka로부터 배치 단위로 수신
- 트랜잭션 내에서 메시지 일괄 처리
- 오프셋 전략: `earliest`
- 자동 커밋: 비활성화
- 최대 폴링 수: 200건
- 병렬 처리: 2개 스레드
- Ack 방식: 수동(`manual`)

### 2. 외부 API 연동을 통한 고유 머신 ID 변환

- Kafka에서 수신한 메트릭 데이터의 machineId를  
  **api-backend의 API를 호출하여 고유 id로 변환**한 뒤,  
  클라이언트로 전달

### 3. 임계값(Threshold) 동적 관리

- 1분마다 외부 API에서 임계값을 조회하여  
  내부 ThresholdStore에 업데이트  
  (Spring `@Scheduled` 어노테이션으로 1분마다 실행)
- 임계값이 0 이하일 경우 경고 로그 출력 및 기존 값 유지
- 임계값은 타입/메트릭별로 Map 구조로 관리  
  (`Map<String, Map<String, Double>> thresholdMap`)

### 4. 웹소켓 실시간 알림

- 조건에 따라 웹소켓을 통해 클라이언트(웹 프론트엔드)로  
  실시간 메트릭 데이터를 전송
- **엔드포인트 예시**
  ```
  ws://{HOST}:{PORT}/ws/metrics
  ```

---

## 로깅 및 운영

- SQL 쿼리, Kafka 클라이언트 동작, 웹 요청 등 다양한 로그 레벨 지원
- 운영 환경에서는 로그 레벨을 적절히 조정할 것

---

## 참고 및 주의사항

- Kafka 브로커와의 연결이 정상적으로 가능해야 하며,  
  필요한 모든 설정이 사전에 준비되어야 합니다.
- 운영 환경에서는 적절한 커넥션 풀 및 장애 대응 전략 설정이 필요합니다.
- **envfile.properties 파일은 .gitignore로 무시되고 있으므로,  
  반드시 사용자가 직접 consumer 모듈의 resource/properties 폴더에  
  envfile.properties 파일을 생성하고 환경 변수를 입력해야 합니다.**

---

## 문의

기술적인 문의는 백엔드 개발팀에 문의해 주세요.


