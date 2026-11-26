"use client";

import { createContext, useContext, useEffect } from "react";
import { requestRefresh, topics } from "@/lib/hooks";
import type { ChangeLogItemResponse, RoomDetailResponse, RoomMemberItem, UserResponse } from "@/lib/types";

export interface RoomContextValue {
  roomId: string;
  room: RoomDetailResponse;
  me: UserResponse;
  /**
   * 방에서의 내 멤버. 백엔드가 내 memberId를 따로 주지 않아서 이름으로 찾는다.
   * 같은 이름이 둘이면 못 찾을 수 있다(null).
   */
  myMember: RoomMemberItem | null;
  reloadRoom: () => Promise<void>;
}

const RoomContext = createContext<RoomContextValue | null>(null);

export const RoomProvider = RoomContext.Provider;

export function useRoom(): RoomContextValue {
  const value = useContext(RoomContext);
  if (!value) throw new Error("useRoom은 방 화면 안에서만 쓴다");
  return value;
}

export function findMyMember(room: RoomDetailResponse, me: UserResponse): RoomMemberItem | null {
  const same = room.members.filter((m) => m.name === me.name);
  return same.length === 1 ? same[0] : null;
}

function parse<T>(e: Event): T | null {
  try {
    return JSON.parse((e as MessageEvent<string>).data) as T;
  } catch {
    return null;
  }
}

/**
 * 방의 실시간 소식(SSE)을 듣는다. 소식이 오면 화면 데이터를 다시 불러오라고 알린다.
 * 안건 하나에 대한 소식은 목록 같은 화면 공통 데이터와 그 안건의 카드만 다시 부르게 해, 카드 수만큼 요청이 늘지 않게 한다.
 * 연결이 끊기면 브라우저가 Last-Event-ID를 붙여 다시 연결한다.
 */
export function useRoomEvents(roomId: string, enabled: boolean) {
  useEffect(() => {
    if (!enabled || typeof EventSource === "undefined") return;
    const source = new EventSource(`/api/rooms/${roomId}/events`);
    const refreshAll = () => requestRefresh();

    // 연결 종료 이벤트
    source.addEventListener("stream.replaced", () => {
      source.close();
    });

    // 데이터 갱신 이벤트
    source.addEventListener("activity", (e) => {
      // 안건의 결정·미결 마감은 그 안건 카드만. 나머지(설문 마감, 방 상태, 장소 등)는 화면 공통 데이터만 바뀐다
      const item = parse<ChangeLogItemResponse>(e);
      // 멤버 퇴장은 재연결 때 member.left 없이 이 기록으로만 오므로, 나간 멤버의 표가 빠진 모든 안건을 다시 부른다
      if (!item || item.entityType === "MEMBER") return refreshAll();
      requestRefresh(item.entityType === "AGENDA" ? [topics.agenda(item.entityId)] : []);
    });
    // 나간 멤버의 표가 모든 안건에서 빠지므로 전부 다시 부른다
    source.addEventListener("member.left", refreshAll);
    // 표 수, 코멘트, 방 만료는 기록이 없는 일시 이벤트라 activity로 오지 않는다
    source.addEventListener("vote.updated", (e) => {
      const data = parse<{ agendaId: number }>(e);
      requestRefresh(data ? [topics.agenda(data.agendaId)] : undefined);
    });
    source.addEventListener("comment.created", (e) => {
      const data = parse<{ agendaId: number | null; placeId: number | null }>(e);
      if (data?.agendaId != null) requestRefresh([topics.agendaComments(data.agendaId)]);
      else if (data?.placeId != null) requestRefresh([topics.placeComments(data.placeId)]);
      else refreshAll();
    });
    source.addEventListener("room.status_changed", refreshAll);
    source.addEventListener("resync.required", refreshAll);
    return () => source.close();
  }, [roomId, enabled]);
}
