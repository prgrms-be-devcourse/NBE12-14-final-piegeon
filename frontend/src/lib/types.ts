// 백엔드 DTO를 그대로 옮긴 타입. 시각은 오프셋 없는 한국 시각 문자열("2026-11-23T14:05:12.123456"),
// 날짜는 "YYYY-MM-DD", 일정 시간은 "HH:mm"이다.

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message?: string;
  data?: T;
}

// ---------- 인증, 사용자 ----------
export interface UserResponse {
  userId: number;
  email: string;
  name: string;
  createdAt: string;
}

// ---------- 방 ----------
export type RoomStatus = "SURVEYING" | "PLANNING" | "REVIEWING" | "FINALIZED" | "EXPIRED";
export type NotJoinableReason = "LOGIN_REQUIRED" | "ALREADY_MEMBER" | "REJOIN_NOT_ALLOWED";

export interface CreateRoomRequest {
  name: string;
  destination: string;
  startDate: string;
  endDate: string;
}

export interface InvitePreviewResponse {
  roomId: number;
  name: string;
  destination: string;
  startDate: string;
  endDate: string;
  status: RoomStatus;
  memberCount: number;
  joinable: boolean;
  notJoinableReason: NotJoinableReason | null;
  surveyRequired: boolean;
}

export interface JoinRoomResponse {
  roomId: number;
  memberId: number;
  surveyRequired: boolean;
}

export interface RoomSummaryResponse {
  roomId: number;
  name: string;
  destination: string;
  startDate: string;
  endDate: string;
  status: RoomStatus;
  memberCount: number;
  opinionDeadlineAt: string | null;
  mySurveySubmitted: boolean;
}

export interface RoomListResponse {
  rooms: RoomSummaryResponse[];
}

export interface RoomMemberItem {
  memberId: number;
  name: string;
  joinedAt: string;
  surveySubmitted: boolean;
}

export interface RoomDetailResponse {
  roomId: number;
  name: string;
  destination: string;
  startDate: string;
  endDate: string;
  inviteCode: string;
  status: RoomStatus;
  readOnly: boolean;
  opinionDeadlineAt: string | null;
  surveyClosedAt: string | null;
  finalizedAt: string | null;
  members: RoomMemberItem[];
}

// ---------- 설문 ----------
export type StayType = "HOTEL" | "PERSONAL" | "SOCIAL" | "CUSTOM";
export type Pace = "RELAXED" | "NORMAL" | "HURRY";
export type ActivityCode = "FOOD" | "NATURE" | "ACTIVITY" | "SHOPPING" | "CULTURE";

export interface SurveyOption {
  value: string;
  label: string;
}

export interface SurveyOptionsResponse {
  stayTypes: SurveyOption[];
  paces: SurveyOption[];
  actPrefs: SurveyOption[];
  limits: {
    budgetMin: number;
    budgetMax: number;
    actPrefsMin: number;
    actPrefsMax: number;
    avoidNoteMaxLength: number;
  };
}

export interface SubmitSurveyRequest {
  budget: number | null;
  stayType: StayType | null;
  pace: Pace | null;
  actPrefs: ActivityCode[];
  avoidNote: string;
}

export interface SubmitSurveyResponse {
  surveyId: number;
  submittedAt: string;
  surveyClosed: boolean;
}

export interface SurveyMeResponse {
  surveyId: number;
  budget: number | null;
  stayType: SurveyOption | null;
  pace: SurveyOption | null;
  actPrefs: SurveyOption[];
  avoidNote: string;
  submittedAt: string;
}

// ---------- 안건(같이 정하기) ----------
// CUSTOM은 멤버가 직접 연 자유 안건이다. 주제 이름이 title이고, 같은 방에서 title이 겹치지 않는다
export type AgendaSubject = "BUDGET" | "STAY" | "PACE" | "FINAL_PLAN" | "CUSTOM";
export type AgendaStatus = "VOTING" | "RESOLVED" | "EXPIRED";

export interface MemberRef {
  memberId: number;
  name: string;
}

export interface OptionRef {
  optionId: number;
  label: string;
  value: string;
}

export interface AgendaListItem {
  agendaId: number;
  subject: AgendaSubject;
  round: number;
  title: string;
  status: AgendaStatus;
  deadlineAt: string;
  totalVoter: number;
  votedCount: number;
  eligible: boolean;
  myOptionId: number | null;
  resolvedOption: OptionRef | null;
}

export interface CreateAgendaRequest {
  title: string;
  options: string[];
}

export interface AgendaListResponse {
  agendas: AgendaListItem[];
}

export interface AgendaOptionResult {
  optionId: number;
  label: string;
  value: string;
  voteCount: number;
  voters: MemberRef[];
  proposedBy: MemberRef | null;
}

export interface AgendaDetailResponse {
  agendaId: number;
  subject: AgendaSubject;
  round: number;
  title: string;
  status: AgendaStatus;
  deadlineAt: string;
  totalVoter: number;
  votedCount: number;
  eligible: boolean;
  myOptionId: number | null;
  options: AgendaOptionResult[];
}

// ---------- 코멘트 ----------
export interface CommentResponse {
  commentId: number;
  memberId: number;
  name: string;
  body: string;
  createdAt: string;
}

export interface CommentListResponse {
  comments: CommentResponse[];
}

// ---------- 장소 ----------
export type PlaceStage = "COMPLEMENT" | "PARTIAL" | "FULL" | "CONFIRMED";
export type PlaceReactionType = "WANT" | "NOPE";
export type PlaceCategory = ActivityCode;

export interface PlaceSearchItem {
  googlePlaceId: string;
  name: string;
  address: string;
  placeType: string | null;
  alreadyRegistered: boolean;
}

export interface PlaceSearchResponse {
  places: PlaceSearchItem[];
  nextPageToken: string | null;
}

export interface PlaceCandidateItem {
  placeId: number;
  googlePlaceId: string;
  name: string;
  address: string;
  placeType: string | null;
  /** 장소 종류. 서버가 placeType을 5종으로 묶어 준다. 표에 없는 종류면 null */
  category?: { value: PlaceCategory; label: string } | null;
  stage: PlaceStage;
  agreeCount: number;
  agreedBy: MemberRef[];
  nopeCount: number;
  nopedBy: MemberRef[];
  suggestedBy: MemberRef;
  confirmedAt: string | null;
}

export interface PlaceBoardResponse {
  activeMemberCount: number;
  limit: number;
  candidates: PlaceCandidateItem[];
}

export interface PlaceDetailResponse {
  placeId: number;
  googlePlaceId: string;
  name: string;
  category: { value: PlaceCategory; label: string } | null;
  address: string;
  googleMapsUrl: string | null;
  // 사진 API가 머지되기 전 백엔드에는 이 칸이 없다
  photoUrl?: string | null;
  stage: PlaceStage;
  agreeCount: number;
  activeMemberCount: number;
  suggestedBy: MemberRef;
  registeredAt: string;
  confirmedAt: string | null;
  reactions: {
    memberId: number;
    name: string;
    reaction: { value: PlaceReactionType; label: string } | null;
  }[];
  commentCount: number;
}

export interface PlaceConfirmResponse {
  placeId: number;
  confirmedAt: string;
}

// ---------- 일정 ----------
export interface ScheduleItemResponse {
  itemId: number;
  placeId: number;
  name: string;
  address: string;
  startTime: string | null;
  memo: string | null;
}

export interface DaySnapshot {
  scheduleDayId: number;
  dayIndex: number;
  date: string;
  version: number;
  items: ScheduleItemResponse[];
}

export interface ScheduleBoardResponse {
  roomStatus: RoomStatus;
  days: DaySnapshot[];
}

export interface AddItemRequest {
  placeId: number;
  afterItemId: number | null;
  startTime: string | null;
  memo: string | null;
  dayVersion: number;
}

export interface AddItemResponse {
  itemId: number;
  day: DaySnapshot;
}

export type ScheduleWarning = "TIME_ORDER_MISMATCH";

/** 두 날을 맞바꾼 결과. 바뀐 두 날이 일차 순서로 온다 */
export interface SwapDayResponse {
  days: DaySnapshot[];
}

export interface DayChangeResponse {
  /** 바뀐 날. 블록을 옮겼으면 블록이 지금 놓인 날 */
  day: DaySnapshot;
  /** 블록을 다른 날로 옮겼을 때만 온다. 블록이 떠난 날 */
  fromDay?: DaySnapshot;
  warnings?: ScheduleWarning[];
}

// ---------- 변경 이력 ----------
export type EntityType = "MEMBER" | "PLAN_ROOM" | "AGENDA" | "PLACE_CANDIDATE" | "SCHEDULE_ITEM";
export type ActionStatus = "CREATE" | "UPDATE" | "DELETE" | "CONFIRM";

export interface ChangeLogItemResponse {
  logId: number;
  actor: MemberRef | null;
  entityType: EntityType;
  entityId: number;
  action: ActionStatus;
  summary: string;
  createdAt: string;
}

export interface ChangeLogPageResponse {
  items: ChangeLogItemResponse[];
  nextCursor: number | null;
}
