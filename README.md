
# ✈️ 착착 (ChakChak)

> 함께 계획하고, 의견을 모아 완성하는 여행

## 프로젝트 소개

착착은 여러 사람이 함께 여행을 계획하고, 여행에 필요한 의견을 조율하며 일정을 완성할 수 있는 여행 계획 서비스입니다.

여행을 준비하는 과정에서 발생하는 일정 조율과 장소 선정의 어려움을 줄이고, 구성원 모두가 편리하게 여행 계획에 참여할 수 있도록 돕습니다.

## 주요 기능

### 여행방 및 설문
- 여행방 생성 및 참여
- 여행 구성원 관리
- 설문을 통한 여행 조건 수집

### 안건 및 투표
- 여행 관련 안건 등록
- 구성원 투표를 통한 의견 조율
- 투표 결과에 따른 안건 결정

### 장소 후보 및 여행 일정
- 여행 장소 후보 등록 및 관리
- 장소에 대한 의견 공유
- 선택한 장소를 바탕으로 여행 일정 구성

### 실시간 동기화
- SSE(Server-Sent Events)를 활용한 실시간 이벤트 전달
- 구성원 입장 및 퇴장, 설문 제출, 투표 및 댓글 변경 사항 공유
- 장소 후보 및 여행 일정 변경 사항 실시간 반영
- 여행방 상태 변경 사항 동기화

### 변경 이력
- 여행 계획 변경 사항 기록
- 변경 내역 및 변경 요약 확인

## 기술 스택

### Backend
- Java 21
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring Session JDBC
- Spring Validation

### Database
- MySQL
- AWS RDS
- Flyway

### External API
- Google Places API

### API Documentation
- Springdoc OpenAPI
- Swagger UI

### Monitoring
- Spring Boot Actuator
- Prometheus
- Micrometer

### Test
- JUnit 5
- Testcontainers
- MySQL Testcontainers

### Code Quality
- Spotless
- Google Java Format (AOSP Style)

## 배포 환경

| 구분 | 환경 |
| --- | --- |
| 백엔드 | AWS EC2 |
| 프론트엔드 | Vercel |
| 데이터베이스 | AWS RDS (MySQL) |

## 시스템 아키텍처

<!-- 시스템 아키텍처 이미지 추가 예정 -->
![시스템 아키텍처](이미지_경로)

## ERD

![ERD](https://github.com/user-attachments/assets/d6b5b7b3-a651-45c3-a78a-5cb47c64d099)

## 실행 방법

### 준비물

- JDK 21
- Docker (로컬 DB와 테스트용 MySQL 컨테이너에 사용)
- Node.js (프론트엔드)

### 1. 데이터베이스

```bash
cd backend
docker compose up -d
```

`backend/compose.yaml`의 MySQL 8.4가 `localhost:3306`에서 실행됩니다. 스키마는 서버가 시작될 때 Flyway가 만듭니다.

### 2. 백엔드

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

서버는 `http://localhost:8080`에서 실행됩니다. 장소 기능까지 사용하려면 실행 전에 `GOOGLE_PLACES_API_KEY` 환경변수에 Google Places API 키를 넣습니다. 키가 없어도 서버는 실행되며, Google을 부르는 기능(장소 검색, 후보 등록, 장소 사진)만 실패합니다. `local` 프로필에서는 가입과 로그인을 반복해서 시험할 수 있도록 호출 제한이 꺼져 있습니다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:3000`에서 열립니다. 브라우저의 `/api/...` 요청은 Next 서버가 백엔드로 전달합니다. 백엔드 주소를 바꾸려면 `frontend/.env.example`을 참고해 `BACKEND_URL`을 설정합니다.

### 4. 빌드와 테스트

```bash
cd backend
./gradlew build          # 전체 테스트 (로컬에서는 컴파일 전에 포맷 자동 적용)
./gradlew spotlessApply  # 코드 포맷 맞추기
```

테스트는 Testcontainers로 MySQL 컨테이너를 실행하므로 Docker가 켜져 있어야 합니다. CI에서는 포맷을 고치지 않고 검사만 하므로, 포맷이 맞지 않은 코드는 CI에서 실패합니다.

## API Documentation

- Swagger UI: 백엔드를 실행한 뒤 `http://localhost:8080/swagger-ui/index.html`에서 확인할 수 있습니다.

## 프로젝트 구조

### Backend

```text
backend
├── src
│   ├── main
│   │   ├── java
│   │   │   └── world.chakchak
│   │   │       ├── domain
│   │   │       │   ├── agenda
│   │   │       │   ├── auth
│   │   │       │   ├── changelog
│   │   │       │   ├── comment
│   │   │       │   ├── place
│   │   │       │   ├── room
│   │   │       │   ├── schedule
│   │   │       │   ├── survey
│   │   │       │   └── user
│   │   │       ├── global
│   │   │       │   ├── config
│   │   │       │   ├── entity
│   │   │       │   ├── error
│   │   │       │   ├── ratelimit
│   │   │       │   ├── response
│   │   │       │   └── security
│   │   │       ├── infra.google
│   │   │       └── ChakchakApplication
│   │   └── resources
│   └── test
├── build.gradle.kts
└── settings.gradle.kts
```

### 패키지별 문서

패키지 이름을 누르면 그 패키지가 맡는 일, API, 주요 클래스, 알아 둘 규칙을 볼 수 있습니다.

| 패키지 | 맡는 일 |
| --- | --- |
| [auth](backend/src/main/java/world/chakchak/domain/auth/README.md) | 회원가입, 로그인, 로그아웃 (세션) |
| [user](backend/src/main/java/world/chakchak/domain/user/README.md) | 사용자 계정 |
| [room](backend/src/main/java/world/chakchak/domain/room/README.md) | 여행방 생성·참여·나가기, 방 진행 단계, 방 단위 잠금 |
| [survey](backend/src/main/java/world/chakchak/domain/survey/README.md) | 여행 설문과 설문 마감 |
| [agenda](backend/src/main/java/world/chakchak/domain/agenda/README.md) | 안건과 만장일치 투표, 최종 확정, 기한 스케줄러 |
| [place](backend/src/main/java/world/chakchak/domain/place/README.md) | 장소 검색, 후보 칸반 보드, 반응과 확정 |
| [schedule](backend/src/main/java/world/chakchak/domain/schedule/README.md) | 일차별 일정 보드 |
| [comment](backend/src/main/java/world/chakchak/domain/comment/README.md) | 안건·장소 코멘트 |
| [changelog](backend/src/main/java/world/chakchak/domain/changelog/README.md) | 방 활동 기록과 SSE 실시간 전파 |
| [global](backend/src/main/java/world/chakchak/global/README.md) | 보안, 응답 형식, 에러 처리, 호출 제한, 시각 |
| [infra](backend/src/main/java/world/chakchak/infra/README.md) | Google Places API 호출 |

## 서비스 화면

<!-- 서비스 화면 이미지 추가 예정 -->

## Backend Team

| <img src="https://avatars.githubusercontent.com/u/301536347?v=4.png" width="100"> | <img src="https://avatars.githubusercontent.com/u/199624538?v=4.png" width="100"> | <img src="https://avatars.githubusercontent.com/u/75709713?v=4.png" width="100"> |
| :---: | :---: | :---: |
| 신시원 | 이동준 | 최세현 |
| [![GitHub](https://img.shields.io/badge/GitHub-swshindev--beep-181717?logo=github)](https://github.com/swshindev-beep) | [![GitHub](https://img.shields.io/badge/GitHub-TASKKILLNAME-181717?logo=github)](https://github.com/TASKKILLNAME) | [![GitHub](https://img.shields.io/badge/GitHub-woneychl-181717?logo=github)](https://github.com/woneychl) |

## Links

- 서비스 URL: <!-- 추가 예정 -->
- 시연 영상: <!-- 추가 예정 -->
