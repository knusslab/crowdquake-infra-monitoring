# metrics-backend 모듈 설명

`metrics-backend`는 컨테이너 및 호스트 머신에서 메트릭 데이터를 수집하고, Kafka를 통해 전송 및 처리하는 백엔드 시스템입니다. 이 프로젝트는 메트릭 수집 → Kafka 전송 → 데이터 처리의 흐름을 중심으로 구성되며, Docker 환경에서 실행됩니다.

## 📁 모듈 구성

- **data-collector**  
  컨테이너 머신의 자원 사용 데이터를 수집합니다.

- **localhost-data-collector**  
  호스트 머신의 자원 사용 데이터를 수집합니다.

- **producer**  
  수집된 데이터를 Kafka로 전송하는 Kafka 프로듀서 역할을 합니다.

- **consumer**  
  Kafka로부터 메트릭 데이터를 수신하며, 내부적으로 WebClient나 WebSocket을 활용해 데이터를 외부에 전송하는 기능도 수행합니다.

---

# api-backend 모듈 설명

`api-backend`는 "Container-Monitoring-Limited-Http" 프로젝트에서 클라이언트(프론트엔드)와 다른 백엔드 서버(metrics-backend)로부터 API 요청을 받아 처리하며, 데이터베이스와 관련된 모든 작업을 담당하는 Java Spring 기반 서버입니다.  
이 시스템은 데이터 저장·조회, 임계치 관리, 실시간 데이터 알림 등 다양한 API 기능을 제공하며, 클라이언트와 백엔드 간의 데이터 흐름을 중계하는 핵심 브릿지 역할을 수행합니다.

주요 기능은 다음과 같습니다:

- DB 초기 데이터 입력, API를 통한 데이터 저장 등 데이터베이스 관련 모든 작업 처리
- 머신(Host, Container) 정보 및 임계치(Threshold) 초과 데이터 관리
- SSE(Server-Sent Events) 방식으로 임계치 초과 데이터를 클라이언트에 실시간 전달
- 날짜별 임계치 초과 데이터 조회, 임계치 설정/조회 등 다양한 API 제공
- 다른 백엔드 서버(metrics-backend) API 호출 시 캐시를 활용한 고유 ID 치환 및 데이터 중계
- 클라이언트와 백엔드 서버 간 데이터 흐름을 중계하는 브릿지 역할

## 📁 api-backend 주요 기능 및 구조

- **데이터 관리**  
  DB 초기화, 데이터 저장, 조회, 수정 등 데이터베이스 관련 모든 API 처리

- **임계치(Threshold) 관리**  
  머신별 임계치 설정/조회, 임계치 초과 데이터 실시간 알림(SSE) 제공

- **API 중계 및 캐싱**  
  metrics-backend 등 외부 백엔드 API 호출 시, 캐시를 활용한 고유 ID 치환 및 데이터 중계

- **실시간 알림**  
  임계치 초과 발생 시 SSE를 통해 클라이언트에 알림 전송

- **클라이언트-백엔드 브릿지**  
  프론트엔드와 metrics-backend 사이의 데이터 흐름을 관리하는 핵심 API 게이트웨이 역할 수행


---

## 📁 (중요) docker-compose 각 파일 설명 및 **사용 방법**

- docker-compose.collector.yml  
  **메트릭을 수집하려는 컴퓨터들의 도커에 설치해 실행시킵니다.**
    - metrics-backend/localhost-data-collector
    - metrics-backend/data-collector (의존성 존재:metrics-backend/producer)

- docker-compose.backend.yml  
  **kafka로 받아온 메트릭을 처리하는 컴퓨터의 도커에 설치해 실행시킵니다.**
    - metrics-backend/consumer
    - api-backend
    - MySQL 데이터베이스

---

## ⚙️ 실행 전 준비사항

- **Docker 설치**  
  이 프로젝트는 Docker 환경에서 동작하므로, 먼저 Docker가 설치되어 있어야 합니다.  
  👉 [Docker 설치 가이드](https://docs.docker.com/get-docker/)



---


## 🚀 실행 방법


#### 1. 환경설정
- 1-1. metrics-backend모듈의 각 모듈들과 api-backend모듈에 환경설정(ex. env파일생성)을 해준다.
    - 환경설정은 아래의 **💻 환경설정** 부분을 참고하세요!

---

#### 2. Docker 실행 전 필수 준비 단계 (터미널 이용 권장)

- 2-1. DB 영구 저장을 위해 **최초 1회** 볼륨 생성해준다.
    - ※ 기존에 있는 DB를 써야한다면, DB설정은 아래의 **🏷️ api-backend 환경설정** 을 참고하세요.
```bash
docker volume create mysql-db
```

- 2-2. 네트워크 **최초 1회** 생성해준다.
```bash
docker network create monitoring_network
```

---
#### 3. 각 docker-compose 실행 (터미널 이용 권장)


- 3-1. 백엔드 + DB + consumer 실행
```bash
docker-compose -f docker-compose.backend.yml up -d --build
```

- 3-2. collector 측 실행 (각 장비 or 서버컴 등에서)
```bash
docker-compose -f docker-compose.collector.yml up -d --build
```


- **순서대로 실행함을 강력히 권장합니다.**
- **테스트를 위해 하나의 컴퓨터에 `docker-compose.collector.yml`, `docker-compose.backend.yml`를 함께 실행시키는 것도 가능합니다.**


---

## 💻 환경설정
- 💡 주석으로 달아놓은 각 경로에 없는 폴더 및 파일이 없다면 **반드시** 새로 생성합니다.

---

### 🏷️ metrics-backend 환경설정

```bash
# consumer/ ... /src/main/resources/properties/envfile.properties

GROUP_ID=[kafka consumer group id]
BOOTSTRAP_SERVER=[kafka 클러스터 ip주소:외부포트번호]
KAFKA_TOPIC_HOST=localhost
KAFKA_TOPIC_CONTAINER=container
KAFKA_GROUP_ID_STORAGE_GROUP=[kafka consumer group id]
API_BASE_URL=http://api-backend:8004
SOCKET_ALLOWED_ADDR=http://localhost:3000 (임시, 프론트 주소가 있다면 해당 경로로 변경)

# data-collector/ ... /src/main/resources/properties/envdc.properties
DATACOLLECTOR_BOOTSTRAP_SERVER=[kafka 클러스터 ip주소:외부포트번호]

# localhost-data-collector/ ... /src/main/resources/properties/envldc.properties
LOCALHOSTDATACOLLECTOR_BOOTSTRAP_SERVER=[kafka 클러스터 ip주소:외부포트번호]

# producer/ ... /src/main/resources/properties/envp.properties
PRODUCER_BOOTSTRAP_SERVER=[kafka 클러스터 ip주소:외부포트번호]

```
---

### 🏷️ api-backend 환경설정

```bash
# api-backend/ ... /src/main/resources/properties/env.properties
DATABASE_URL=jdbc:mysql://<엔드포인트>/monitoring_db
DATABASE_USERNAME=<Username>
DATABASE_PASSWORD=<Password>

cors.allowed-origins=<주소1>,<주소2>,...
```

```bash
# 테스트 시
# 도커에 임시 MySQL DB를 생성했을 때 env.properties 설정
DATABASE_URL=jdbc:mysql://mysql-db:3306/monitoring_db
DATABASE_USERNAME=monitoring_user
DATABASE_PASSWORD=monitoring_pass

cors.allowed-origins=http://localhost:3000
```

