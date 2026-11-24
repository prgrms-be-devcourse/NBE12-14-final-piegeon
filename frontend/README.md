# 착착 프론트엔드

Next.js 15 (App Router) + TypeScript. 화면은 "착착 디자인 v4"를 따른다.

## 실행

```bash
cd frontend
npm install
npm run dev        # http://localhost:3000
```

백엔드는 `backend/`에서 따로 띄운다 (`docker compose up -d`, `./gradlew bootRun --args='--spring.profiles.active=local'`).

## 백엔드와 붙는 방식

- 브라우저는 항상 같은 주소의 `/api/...`를 부른다. Next 서버가 `next.config.ts`의 rewrite로 백엔드에 넘긴다.
- 그래서 세션 쿠키(`SESSION`)가 프론트 주소에 붙고, CORS 설정이 필요 없다. 장소 사진(302), SSE(`/api/rooms/{id}/events`)도 같은 길로 간다.
- 백엔드 주소는 환경변수 `BACKEND_URL` (기본 `http://localhost:8080`). `.env.example` 참고.

## 명령

| 명령 | 하는 일 |
| --- | --- |
| `npm run dev` | 개발 서버 |
| `npm run build` | 배포용 빌드 (타입 검사 포함) |
| `npm run start` | 빌드한 것 실행 |
| `npm run typecheck` | 타입 검사만 |

## 폴더

```
src/
├── app/          # 화면. rooms/[roomId]/layout.tsx가 방 안의 왼쪽 메뉴와 실시간 연결을 맡는다
├── components/   # 공통 조각 (버튼, 아바타, 안건 카드 등)
└── lib/          # api.ts(호출), types.ts(백엔드 DTO), format.ts(날짜·금액), hooks.ts
```

## 알아 둘 것

- 서버 시각은 오프셋 없는 한국 시각이다. `lib/format.ts`가 브라우저 시간대와 상관없이 한국 시각으로 계산한다.
- 방에서의 내 멤버는 백엔드가 memberId를 따로 주지 않아서 이름으로 찾는다 (`components/room.tsx`).
- Pretendard 글꼴은 SIL Open Font License 1.1이다.
- 사진은 Wikimedia Commons(CC BY-SA)다. 새 사진을 넣으면 `src/lib/photos.ts`에 저작자, 라이선스, 원본 주소를 같이 적는다. 출처 페이지(`/credits`)에 자동으로 나온다.
