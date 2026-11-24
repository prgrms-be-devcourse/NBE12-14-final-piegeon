
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

## API Documentation

- Swagger UI: Spring Boot 애플리케이션 실행 후 `/swagger-ui/index.html`에서 확인할 수 있습니다.

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
│   │   │       │   ├── response
│   │   │       │   └── security
│   │   │       ├── infra.google
│   │   │       └── ChakchakaApplication
│   │   └── resources
│   └── test
├── build.gradle.kts
└── settings.gradle.kts
```

## 서비스 화면

<!-- 서비스 화면 이미지 추가 예정 -->

## Backend Team

| <img src="https://avatars.githubusercontent.com/u/301536347?v=4.png" width="100"> | <img src="https://avatars.githubusercontent.com/u/199624538?v=4.png" width="100"> | <img src="https://avatars.githubusercontent.com/u/75709713?v=4.png" width="100"> |
| :---: | :---: | :---: |
| 신시원 | 이동준 | 최세현 |
| [![GitHub](https://shields.io)](https://github.com/swshindev-beep) | [![GitHub](https://shields.io)](hhttps://github.com/TASKKILLNAME) | [![GitHub](https://shields.io)](https://github.com/woneychl) |

## Links

- 서비스 URL: <!-- 추가 예정 -->
- 시연 영상: <!-- 추가 예정 -->
