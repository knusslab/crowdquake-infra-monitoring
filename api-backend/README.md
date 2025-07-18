﻿# api-backend

## 프로젝트 개요

**api-backend**는 클라이언트(frontend)와 다른 백엔드 서버(metrics-backend)로부터 API 요청을 받아 처리하며, 데이터베이스와 관련된 모든 역할을 담당하는 Java Spring 기반 서버입니다.

이 프로젝트는 다음과 같은 주요 기능을 수행합니다:

- DB 초기 데이터 입력, API를 통한 데이터 저장 등 DB와 관련된 모든 작업 처리
- 머신(Host, Container) 정보 관리 및 임계치(Threshold) 초과 데이터 관리
- Websocket 방식으로 실시간 메트릭 데이터 클라이언트에 전달
- SSE(Server-Sent Events) 방식으로 임계치 초과 데이터를 실시간으로 클라이언트에 전달
- API 요청에 따라 날짜별 임계치 초과 데이터 조회, 임계치 설정/조회 등 다양한 기능 제공
- 클라이언트와 다른 백엔드 서버 사이의 데이터 흐름을 중계하는 핵심 브릿지 역할 수행


---

## 주요 기술 스택 및 의존성

- Java 21
- Spring Boot 3.4.4
- Spring Data JPA & JDBC
- Spring Kafka
- Spring Web
- Spring WebSocket
- Spring Actuator
- Spring WebFlux
- MySQL
- Jackson (JavaTime 지원)
- Lombok
- JUnit
- Caffeine (캐시)
- SpringDoc OpenAPI (Swagger 문서화)

---

## API 목록

### 1. 다른 백엔드 서버와 단방향 통신하는 API

| 메서드 | 엔드포인트          | 설명             |
|--------|----------------|----------------|
| POST   | `/api/metrics` | 실시간 메트릭 데이터 수집 |

### 2. 클라이언트와 통신하는 API


| 메서드    | 엔드포인트                                 | 설명                                         |
|-----------|-------------------------------------------|----------------------------------------------|
| GET       | `/api/metrics/threshold-setting`          | 임계치(Threshold) 정보 조회                  |
| POST      | `/api/metrics/threshold-setting`          | 임계치(Threshold) 정보 설정                  |
| GET       | `/api/metrics/under-threshold-setting`    | 임계치(Under Threshold) 정보 조회            |
| POST      | `/api/metrics/under-threshold-setting`    | 임계치(Under Threshold) 정보 설정            |
| GET       | `/api/metrics/history`                    | 임계치 초과 이력 조회 (날짜, 머신 타입, 호스트 이름, 머신 이름, 메시지 타입, 메트릭 이름 등 다양한 조건을 쿼리 파라미터 또는 POST 바디로 전달하여 필터링 가능) |
| GET       | `/api/metrics/threshold-history-all`      | 모든 머신의 임계값 초과 이력(최신 50개) 조회 |
| GET       | `/api/metrics/threshold-alert`            | SSE 방식으로 임계치 초과 실시간 알림 전송     |
| GET       | `/api/inventory/list`                     | 등록된 모든 호스트 및 컨테이너 목록 조회     |
| GET/POST  | `/ws/metrics`                             | 웹소켓 기반 실시간 메트릭 통신              |

---

## 프로젝트 주요 기능

- 머신(Host, Container) 정보 관리
- 메트릭 데이터 실시간 수집 및 전송(Websocket)
- 임계치(Threshold) 관리 및 실시간 알림(SSE)
- 필터링을 통한 데이터 조회, 임계치 설정/조회
- API 중계 및 데이터 브릿지 역할

---

## 주의사항

- 개발 및 테스트 환경에서는 반드시 `env.properties` 파일을 직접 생성 후 실행하세요.
- 민감 정보는 외부에 노출되지 않도록 주의하세요.

---

## 문의 및 기여

- 이 프로젝트에 대한 문의, 개선 제안, 버그 제보는 이슈 또는 PR로 남겨주세요.

