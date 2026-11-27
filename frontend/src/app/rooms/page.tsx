"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { TopBar } from "@/components/TopBar";
import { Cover, Loading, Notice } from "@/components/ui";
import { api } from "@/lib/api";
import { dDay, dateLabel, tripLength } from "@/lib/format";
import { useApi, useMe } from "@/lib/hooks";
import type { RoomStatus, RoomSummaryResponse } from "@/lib/types";

const STATUS_TEXT: Record<RoomStatus, string> = {
  SURVEYING: "설문과 같이 정하기 중",
  PLANNING: "가고 싶은 곳과 일정을 짜는 중",
  REVIEWING: "최종 확정 투표 중",
  FINALIZED: "확정됐어요",
  // 목록에는 닫힌 까닭이 없다. 아무도 설문을 안 냈을 때와 출발일까지 못 정했을 때 모두 맞는 말로 쓴다
  EXPIRED: "정하지 못하고 닫혔어요",
};

function RoomCard({ room }: { room: RoomSummaryResponse }) {
  const past = room.status === "FINALIZED" || room.status === "EXPIRED";
  const href =
    room.status === "FINALIZED" ? `/rooms/${room.roomId}/plan` : `/rooms/${room.roomId}`;
  const todo =
    room.status === "SURVEYING" && !room.mySurveySubmitted ? "내 설문이 남았어요" : STATUS_TEXT[room.status];
  return (
    <Link href={href} className="card" style={{ textDecoration: "none", color: "var(--ink)", padding: 0, overflow: "hidden", gap: 0 }}>
      <span style={{ position: "relative", display: "block" }}>
        <Cover destination={room.destination} style={{ height: 140, fontSize: 48, opacity: past ? 0.6 : 1 }} />
        <span className="tag" style={{ position: "absolute", top: 12, left: 12, background: "var(--surface)", color: "var(--ink)" }}>
          {dDay(room.startDate, room.endDate)}
        </span>
      </span>
      <span className="stack" style={{ padding: "16px 18px 18px", gap: 6 }}>
        <b style={{ fontSize: 18 }}>{room.name}</b>
        <span className="small muted">
          {room.destination}, {dateLabel(room.startDate)}부터 {tripLength(room.startDate, room.endDate)}
        </span>
        <span className="row small" style={{ justifyContent: "space-between" }}>
          <span className="muted">{room.memberCount}명</span>
          <span style={{ color: past ? "var(--ink-2)" : "var(--accent)" }}>{todo}</span>
        </span>
      </span>
    </Link>
  );
}

export default function RoomsPage() {
  const router = useRouter();
  const me = useMe();
  const rooms = useApi("rooms", api.rooms);
  const [code, setCode] = useState("");

  const list = rooms.data?.rooms ?? [];
  const now = list.filter((r) => r.status !== "FINALIZED" && r.status !== "EXPIRED");
  const past = list.filter((r) => r.status === "FINALIZED" || r.status === "EXPIRED");

  return (
    <>
      <TopBar loggedIn />
      <main className="page">
        <div>
          <h1 style={{ margin: 0, fontSize: 34 }}>{me.data ? `${me.data.name}님의 여행` : "내 여행"}</h1>
          <p className="muted" style={{ marginTop: 6 }}>
            {rooms.data ? `함께 준비 중인 여행이 ${now.length}개 있어요` : " "}
          </p>
        </div>

        <form
          className="card"
          style={{ flexDirection: "row", alignItems: "flex-end", flexWrap: "wrap" }}
          onSubmit={(e) => {
            e.preventDefault();
            const c = code.trim();
            if (c) router.push(`/invite/${encodeURIComponent(c)}`);
          }}
        >
          <div className="f grow" style={{ minWidth: 220 }}>
            <label htmlFor="code">초대 코드로 참여</label>
            <input id="code" className="in" placeholder="친구에게 받은 8자리 코드" value={code} onChange={(e) => setCode(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-line" disabled={!code.trim()}>
            참여하기
          </button>
        </form>

        {rooms.loading && <Loading />}
        {rooms.error && <Notice kind="err">{rooms.error.message}</Notice>}

        {rooms.data && (
          <section aria-labelledby="now-h">
            <div className="sec-h">
              <h2 id="now-h">준비 중인 여행</h2>
              <span className="muted">{now.length}</span>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 16 }}>
              {now.map((r) => (
                <RoomCard key={r.roomId} room={r} />
              ))}
              <Link
                href="/rooms/new"
                className="card"
                style={{ textDecoration: "none", color: "var(--ink)", alignItems: "center", justifyContent: "center", minHeight: 240, boxShadow: "inset 0 0 0 1.5px var(--line)", background: "transparent" }}
              >
                <span style={{ fontSize: 32, color: "var(--accent)" }}>+</span>
                <b>새 여행방 만들기</b>
                <span className="small muted">날짜와 여행지만 정하면 돼요</span>
              </Link>
            </div>
          </section>
        )}

        {past.length > 0 && (
          <section aria-labelledby="past-h">
            <div className="sec-h">
              <h2 id="past-h">지난 여행</h2>
              <span className="muted">{past.length}</span>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 16 }}>
              {past.map((r) => (
                <RoomCard key={r.roomId} room={r} />
              ))}
            </div>
          </section>
        )}
      </main>
    </>
  );
}
