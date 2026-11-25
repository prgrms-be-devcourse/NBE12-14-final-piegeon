"use client";

import { createContext, useContext, useEffect } from "react";
import { requestRefresh } from "@/lib/hooks";
import type { RoomDetailResponse, RoomMemberItem, UserResponse } from "@/lib/types";

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

/**
 * 방의 실시간 소식(SSE)을 듣는다. 소식이 오면 화면 데이터를 다시 불러오라고 알린다.
 * 연결이 끊기면 브라우저가 Last-Event-ID를 붙여 다시 연결한다.
 */
export function useRoomEvents(roomId: string, enabled: boolean) {
  useEffect(() => {
    if (!enabled || typeof EventSource === "undefined") return;
    const source = new EventSource(`/api/rooms/${roomId}/events`);
    const refresh = () => requestRefresh();
    source.addEventListener("activity", refresh);
    source.addEventListener("member.left", refresh);
    // 표 수, 코멘트, 방 만료는 기록이 없는 일시 이벤트라 activity로 오지 않는다
    source.addEventListener("vote.updated", refresh);
    source.addEventListener("comment.created", refresh);
    source.addEventListener("room.status_changed", refresh);
    source.addEventListener("resync.required", refresh);
    return () => source.close();
  }, [roomId, enabled]);
}
