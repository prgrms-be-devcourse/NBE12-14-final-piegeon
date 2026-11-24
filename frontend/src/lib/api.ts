import type {
  AddItemRequest,
  AddItemResponse,
  AgendaDetailResponse,
  AgendaListResponse,
  ApiEnvelope,
  ChangeLogPageResponse,
  CommentListResponse,
  CommentResponse,
  CreateRoomRequest,
  DayChangeResponse,
  InvitePreviewResponse,
  JoinRoomResponse,
  PlaceBoardResponse,
  PlaceCandidateItem,
  PlaceConfirmResponse,
  PlaceDetailResponse,
  PlaceReactionType,
  PlaceSearchResponse,
  RoomDetailResponse,
  RoomListResponse,
  ScheduleBoardResponse,
  SubmitSurveyRequest,
  SubmitSurveyResponse,
  SurveyMeResponse,
  SurveyOptionsResponse,
  UserResponse,
} from "./types";

/** 백엔드가 돌려준 실패. code는 ErrorCode 이름(예: "SCHEDULE_VERSION_CONFLICT"), data는 에러마다 다른 추가 정보 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly data?: unknown,
  ) {
    super(message);
  }
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/**
 * 같은 주소의 /api로 요청하고 공통 응답(ApiResponse)에서 data만 꺼낸다.
 * 실패하면 ApiError를 던진다. 본문이 없는 응답(204 등)은 undefined.
 */
async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      credentials: "same-origin",
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
  }

  const text = await res.text();
  let envelope: ApiEnvelope<T> | null = null;
  if (text) {
    try {
      envelope = JSON.parse(text) as ApiEnvelope<T>;
    } catch {
      envelope = null;
    }
  }

  if (!res.ok || (envelope && envelope.success === false)) {
    throw new ApiError(
      res.status,
      envelope?.code ?? "INTERNAL_ERROR",
      envelope?.message ?? "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.",
      envelope?.data,
    );
  }
  return envelope?.data as T;
}

const get = <T>(path: string) => request<T>("GET", path);
const post = <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? undefined);
const put = <T>(path: string, body?: unknown) => request<T>("PUT", path, body);
const patch = <T>(path: string, body?: unknown) => request<T>("PATCH", path, body);
const del = <T>(path: string) => request<T>("DELETE", path);

const room = (roomId: number | string) => `/api/rooms/${roomId}`;

export const api = {
  // 인증
  signup: (email: string, name: string, password: string) =>
    post<UserResponse>("/api/auth/signup", { email, name, password }),
  login: (email: string, password: string) =>
    post<UserResponse>("/api/auth/login", { email, password }),
  logout: () => post<void>("/api/auth/logout"),
  me: () => get<UserResponse>("/api/users/me"),

  // 방
  rooms: () => get<RoomListResponse>("/api/rooms"),
  createRoom: (req: CreateRoomRequest) => post<RoomDetailResponse>("/api/rooms", req),
  room: (roomId: number | string) => get<RoomDetailResponse>(room(roomId)),
  invitePreview: (code: string) =>
    get<InvitePreviewResponse>(`/api/rooms/invites/${encodeURIComponent(code)}`),
  join: (code: string) =>
    post<JoinRoomResponse>(`/api/rooms/invites/${encodeURIComponent(code)}/join`),
  seen: (roomId: number | string, seenUntil: string) =>
    post<void>(`${room(roomId)}/seen`, { seenUntil }),
  leave: (roomId: number | string) => del<void>(`${room(roomId)}/members/me`),

  // 설문
  surveyOptions: () => get<SurveyOptionsResponse>("/api/survey-options"),
  submitSurvey: (roomId: number | string, req: SubmitSurveyRequest) =>
    post<SubmitSurveyResponse>(`${room(roomId)}/survey`, req),
  mySurvey: (roomId: number | string) => get<SurveyMeResponse>(`${room(roomId)}/survey/me`),

  // 같이 정하기
  agendas: (roomId: number | string) => get<AgendaListResponse>(`${room(roomId)}/agendas`),
  agenda: (roomId: number | string, agendaId: number) =>
    get<AgendaDetailResponse>(`${room(roomId)}/agendas/${agendaId}`),
  vote: (roomId: number | string, agendaId: number, optionId: number, comment?: string) =>
    put<AgendaDetailResponse>(`${room(roomId)}/agendas/${agendaId}/vote`, {
      optionId,
      comment: comment || null,
    }),
  agendaComments: (roomId: number | string, agendaId: number) =>
    get<CommentListResponse>(`${room(roomId)}/agendas/${agendaId}/comments`),
  addAgendaComment: (roomId: number | string, agendaId: number, body: string) =>
    post<CommentResponse>(`${room(roomId)}/agendas/${agendaId}/comments`, { body }),
  addBudgetOption: (roomId: number | string, agendaId: number, amount: number) =>
    post<AgendaDetailResponse>(`${room(roomId)}/agendas/${agendaId}/options`, { amount }),
  deleteOption: (roomId: number | string, agendaId: number, optionId: number) =>
    del<AgendaDetailResponse>(`${room(roomId)}/agendas/${agendaId}/options/${optionId}`),
  nextRound: (roomId: number | string, agendaId: number) =>
    post<AgendaDetailResponse>(`${room(roomId)}/agendas/${agendaId}/next-round`),
  openFinalReview: (roomId: number | string) =>
    post<AgendaDetailResponse>(`${room(roomId)}/final-review`),

  // 장소
  searchPlaces: (roomId: number | string, query: string) =>
    get<PlaceSearchResponse>(`${room(roomId)}/places/search?query=${encodeURIComponent(query)}`),
  registerPlace: (roomId: number | string, googlePlaceId: string) =>
    post<PlaceCandidateItem>(`${room(roomId)}/place-candidates`, { googlePlaceId }),
  placeBoard: (roomId: number | string) =>
    get<PlaceBoardResponse>(`${room(roomId)}/place-candidates`),
  place: (roomId: number | string, placeId: number) =>
    get<PlaceDetailResponse>(`${room(roomId)}/place-candidates/${placeId}`),
  react: (roomId: number | string, placeId: number, reaction: PlaceReactionType) =>
    put<PlaceDetailResponse>(`${room(roomId)}/place-candidates/${placeId}/reaction`, { reaction }),
  cancelReaction: (roomId: number | string, placeId: number) =>
    del<PlaceDetailResponse>(`${room(roomId)}/place-candidates/${placeId}/reaction`),
  confirmPlace: (roomId: number | string, placeId: number) =>
    post<PlaceConfirmResponse>(`${room(roomId)}/place-candidates/${placeId}/confirm`),
  deletePlace: (roomId: number | string, placeId: number) =>
    del<void>(`${room(roomId)}/place-candidates/${placeId}`),
  placeComments: (roomId: number | string, placeId: number) =>
    get<CommentListResponse>(`${room(roomId)}/place-candidates/${placeId}/comments`),
  addPlaceComment: (roomId: number | string, placeId: number, body: string) =>
    post<CommentResponse>(`${room(roomId)}/place-candidates/${placeId}/comments`, { body }),

  // 일정
  schedule: (roomId: number | string) => get<ScheduleBoardResponse>(`${room(roomId)}/schedule`),
  addItem: (roomId: number | string, dayId: number, req: AddItemRequest) =>
    post<AddItemResponse>(`${room(roomId)}/schedule-days/${dayId}/items`, req),
  moveItem: (roomId: number | string, itemId: number, afterItemId: number | null, dayVersion: number) =>
    patch<DayChangeResponse>(`${room(roomId)}/schedule-items/${itemId}/position`, {
      afterItemId,
      dayVersion,
    }),
  updateItem: (
    roomId: number | string,
    itemId: number,
    startTime: string | null,
    memo: string | null,
    dayVersion: number,
  ) =>
    patch<DayChangeResponse>(`${room(roomId)}/schedule-items/${itemId}`, {
      startTime,
      memo,
      dayVersion,
    }),
  deleteItem: (roomId: number | string, itemId: number, dayVersion: number) =>
    del<DayChangeResponse>(`${room(roomId)}/schedule-items/${itemId}?dayVersion=${dayVersion}`),

  // 변경 이력
  changeLogs: (roomId: number | string, cursor?: number | null, size = 20) =>
    get<ChangeLogPageResponse>(
      `${room(roomId)}/change-logs?size=${size}${cursor ? `&cursor=${cursor}` : ""}`,
    ),
};

/** 화면에 띄울 에러 문구. 백엔드 message가 있으면 그대로 쓴다 */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return "알 수 없는 오류가 났어요. 잠시 후 다시 시도해 주세요.";
}
