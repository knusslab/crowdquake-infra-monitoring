# 🔎 server-monitoring

`server-monitoring`은 실제 서버실의 서버 컴퓨터에 설치하여,  
**호스트 머신과 해당 서버에 속한 모든 컨테이너의 메트릭(자원 사용량 등)을 실시간으로 수집하고 모니터링할 수 있도록 설계된 시스템**입니다.

Docker 환경에서 collector를 실행하면 서버 컴퓨터의 호스트 및 컨테이너 자원 데이터를 자동으로 수집하여 Kafka 클러스터로 전송합니다.  
중개 컴퓨터에서는 Docker에서 실행된 consumer가 Kafka에서 이 데이터를 받아 backend로 전송합니다. 
메인 컴퓨터에서는 Docker에서 실행된 backend가 consumer에서 받아온 데이터를 사용해 실시간 메트릭 데이터를 전송하거나 각 머신별 임계치 초과 여부를 계산하고,  
임계치 초과 시 실시간 알림을 전송하여 운영자가 신속하게 서버 상태를 모니터링하고 대응할 수 있도록 지원합니다.

---

## 📁 (중요) docker-compose 각 파일 설명 및 **사용 방법**

- docker-compose.collector.yml  
  **메트릭을 수집하려는 컴퓨터들의 도커에 설치해 실행시킵니다.**
  - metrics-backend/data-collector (의존성 존재:metrics-backend/producer)

- docker-compose.consumer.yml  
  **kafka cluster에서 메트릭을 받아오는 컴퓨터의 도커에 설치해 실행시킵니다.**
  - metrics-backend/consumer

- docker-compose.backend.yml  
  **consumer로 받아온 메트릭을 처리하는 컴퓨터의 도커에 설치해 실행시킵니다.**
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
- 1-1. 폴더 최상위 루트에 .env파일을 만들어 환경설정을 해준다.
  - 환경설정은 아래의 **💻 환경설정** 부분을 참고하세요!

---
#### 2. 해당 컴퓨터에 시스템을 올리기 전 이미지 파일 생성 단계
-**2-1. ~ 2-4.의 단계는 해당 프로젝트 폴더의 최상위 루트에서 실행시킵니다.**

- 2-1. 이미지 : isslab/im-api-backend 생성
```bash
docker build -t isslab/im-api-backend:latest -f api-backend/Dockerfile .
```

- 2-2. 이미지 : isslab/im-metrics-consumer 생성
```bash
docker build -t isslab/im-metrics-consumer:latest -f metrics-backend/consumer/Dockerfile .
```

- 2-3. 이미지 : isslab/im-data-collector 생성
```bash
docker build -t isslab/im-data-collector:latest -f metrics-backend/data-collector/Dockerfile .

```

---
#### 3. 각 해당 컴퓨터에서 각 docker-compose 실행


- 3-1. 백엔드 + DB 실행
```bash
docker-compose -f docker-compose.backend.yml up -d
```

- 3-2. consumer 실행
```bash
docker-compose -f docker-compose.consumer.yml up -d
```

- 3-3. collector 측 실행 (각 장비 or 서버컴 등에서)
```bash
docker-compose -f docker-compose.collector.yml up -d
```


- **순서대로 실행함을 !강력히! 권장합니다.**
- **테스트를 위해 하나의 컴퓨터에 `docker-compose.collector.yml`, `docker-compose.consumer.yml`, `docker-compose.backend.yml`를 함께 실행시키는 것도 가능합니다.**


---

## 💻 환경설정
- 💡 최상위 경로에 .env 파일이 없다면 **반드시** 새로 생성합니다.
```bash
TZ=Asia/Seoul   # 변경 가능
DATABASE_ROOT_PASSWORD=<Root-Password>(임의로 설정)
DATABASE_USERNAME=<Username>(임의로 설정)
DATABASE_PASSWORD=<Password>(임의로 설정)
CORS_ALLOWED_ORIGINS=<주소1>,<주소2>,... [CORS 허용 Origin 목록(콤마로 구분)]
BOOTSTRAP_SERVER=[kafka 클러스터 ip주소:외부포트번호]
KAFKA_TOPIC_NAME=[kafka topic name]
KAFKA_CONSUMER_GROUP_ID=[kafka consumer group id]
API_BASE_URL=http://api-backend:8004    # 필수
```

---

## server-monitoring 주요 특징

- **서버실의 각 서버 컴퓨터에 collector를 Docker로 설치**  
  호스트 및 모든 컨테이너의 메트릭(자원 사용량 등)을 자동 수집

- **Kafka 클러스터 연동**  
  수집된 데이터는 Kafka 클러스터를 통해 메인 서버로 전송

- **임계치(Threshold) 모니터링 및 알림**  
  메인 서버의 backend가 Kafka에서 데이터를 받아  
  각 서버/컨테이너별 임계치 초과 여부를 계산  
  임계치 초과 시 실시간 알림 제공

- **운영 편의성**  
  운영자는 전체 서버실의 자원 현황과 이상 상황을 한눈에 모니터링 가능

---

## 구성 예시

- **각 서버 컴퓨터**
  - collector (`docker-compose.collector.yml` 참고) 실행
  - host와 container의 메트릭을 수집해 Kafka로 메트릭 데이터 전송

- **중개 컴퓨터(kafka cluster에 연결)**
  - consumer (`docker-compose.consumer.yml` 참고) 실행
  - kafka에서 데이터 수신 및 backend로 데이터 단방향 전송

- **메인 컴퓨터**
  - backend (`docker-compose.backend.yml` 참고) 실행
  - 데이터 수신 및 메트릭 데이터 모니터링, 임계치 모니터링/알림

---

이 시스템을 통해 실제 서버실의 다양한 서버와 컨테이너의 자원 사용 현황을  
중앙에서 실시간으로 모니터링하고, 임계치 초과 등 이상 상황에 즉시 대응할 수 있습니다.

---

# metrics-backend 모듈 설명

`metrics-backend`는 컨테이너 및 호스트 머신에서 메트릭 데이터를 수집하고, Kafka를 통해 전송 및 처리하는 백엔드 시스템입니다. 이 프로젝트는 메트릭 수집 → Kafka 전송 → 데이터 수집 및 전송의 흐름을 중심으로 구성되며, Docker 환경에서 실행됩니다.

## 📁 모듈 구성

- **container-data-collector**  
  컨테이너 머신의 자원 사용 데이터를 수집합니다.

- **machine-data-collector**  
  호스트 머신의 자원 사용 데이터를 수집합니다.

- **producer**  
  수집된 데이터를 Kafka로 전송하는 Kafka 프로듀서 역할을 합니다.

- **consumer**  
  Kafka로부터 메트릭 데이터를 수신하며, 내부적으로 WebClient를 활용해 데이터를 backend에 전송하는 기능을 수행합니다.

---

# api-backend 모듈 설명

`api-backend`는 클라이언트(프론트엔드)와 다른 백엔드 서버(metrics-backend)로부터 API 요청을 받아 처리하며, 데이터베이스와 관련된 모든 작업을 담당하는 Java Spring 기반 서버입니다.  
이 시스템은 데이터 저장·조회, 임계치 관리, 실시간 데이터 알림 등 다양한 API 기능을 제공하며, 클라이언트와 백엔드 간의 데이터 흐름을 중계하는 핵심 브릿지 역할을 수행합니다.

주요 기능은 다음과 같습니다:

- DB 초기 데이터 입력, API를 통한 데이터 저장 등 데이터베이스 관련 모든 작업 처리
- 머신(Host, Container) 정보 및 임계치(Threshold) 초과 데이터 관리
- SSE(Server-Sent Events) 방식으로 임계치 초과 데이터를 클라이언트에 실시간 전달
- 날짜별 임계치 초과 데이터 조회, 임계치 설정/조회 등 다양한 API 제공
- 클라이언트와 백엔드 서버 간 데이터 흐름을 중계하는 브릿지 역할

## 📁 api-backend 주요 기능 및 구조

- **데이터 관리**  
  DB 초기화, 데이터 저장, 조회, 수정 등 데이터베이스 관련 모든 API 처리

- **임계치(Threshold) 관리**  
  머신별 임계치 설정/조회, 임계치 초과 데이터 실시간 알림(SSE) 제공

- **실시간 알림**  
  임계치 초과 발생 시 SSE를 통해 클라이언트에 알림 전송

- **클라이언트-백엔드 브릿지**  
  프론트엔드와 metrics-backend 사이의 데이터 흐름을 관리하는 핵심 API 게이트웨이 역할 수행


---

## 문의 및 기여

- 이 프로젝트에 대한 문의, 개선 제안, 버그 제보는 이슈 또는 PR로 남겨주세요.

