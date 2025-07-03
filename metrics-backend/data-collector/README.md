### 1. 프로젝트 소개 (Project Title & Description)

> `data-collector`는 Java 기반 리소스 수집 시스템으로, 로컬 호스트(OSHI 기반) 및 컨테이너(cgroup 기반)의 CPU, 메모리, 디스크 I/O, 네트워크 사용량을 주기적으로 수집합니다. 이 데이터는 Kafka로 전송하고 모니터링 용도로 활용할 수 있습니다.

---

### 2. 주요 기능 (Key Features)

* 로컬 호스트 리소스 수집 (via OSHI)
* 컨테이너 리소스 수집 (via cgroup)
* 디스크/네트워크 변화량 계산
* 네트워크 전송/수신 속도(Bps) 계산
* Kafka 연동

---

### 3. 프로젝트 구조 (Project Structure) 

간략한 패키지 구조 예:

```
├── src/main/java/kr/cs/interdata/datacollector/
│   ├── LocalHostResourceMonitor.java
│   ├── LocalHostNetworkMonitor.java
│   ├── ContainerResourceMonitor.java
│   ├── DataCollectorApplication.java
│   └── ResourceMonitorDaemon.java
```

---

### 4. 실행 방법 (How to Run)

#### 로컬 호스트 리소스 수집 실행

로컬 시스템(호스트)의 리소스를 수집하려면 다음 명령어를 실행하세요:

```bash
# 로컬 호스트의 리소스 모니터링을 실행합니다.
./gradlew runLocalHostMonitor
```


#### 컨테이너 리소스 수집 실행

도커 환경에서 컨테이너 리소스를 수집하려면 아래 단계를 순서대로 따라 주세요:

```bash
# 1. 프로젝트를 빌드합니다.
./gradlew build

# 2. 도커 이미지를 생성합니다. (resource-monitor라는 이름으로 태그함)
docker build -t resource-monitor .

# 3. (선택) 이전에 실행 중이던 동일 이름의 컨테이너가 있다면 삭제합니다.
docker rm test-monitor

# 4. 컨테이너를 실행합니다.
# --privileged 옵션은 시스템 리소스 접근을 위해 필요합니다.
# 'test-monitor'라는 이름으로 컨테이너를 실행합니다.
docker run --privileged --name test-monitor resource-monitor
```

---

### 5. 환경 및 요구 사항 (Requirements)

* Java 17+
* Gradle
* Docker (for container mode)
* Linux (컨테이너 모니터링은 cgroup 의존)

---

### 6. 출력 예시 (Sample Output)

### 사용하는 데이터
![image](https://github.com/user-attachments/assets/90e46a3e-5099-4bb0-932d-bfd51f0fac55)


---



