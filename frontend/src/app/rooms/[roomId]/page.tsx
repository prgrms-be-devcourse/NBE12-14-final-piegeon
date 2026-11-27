"use client";

import Link from "next/link";
import { useRoom } from "@/components/room";
import { Avatar, CopyButton, Cover, Faces } from "@/components/ui";
import { api } from "@/lib/api";
import { dDay, dateLabel, dateTimeLabel, feedTime, tripLength } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { AgendaListItem, RoomDetailResponse } from "@/lib/types";

interface NextStep {
  title: string;
  desc: string;
  href?: string;
  cta?: string;
}

function nextStep(room: RoomDetailResponse, mySubmitted: boolean, myTurn: AgendaListItem[], base: string): NextStep {
  const submitted = room.members.filter((m) => m.surveySubmitted).length;
  switch (room.status) {
    case "SURVEYING":
      if (!mySubmitted)
        return { title: "설문부터 해 주세요", desc: "1분이면 돼요. 모두 내면 답이 갈린 것만 같이 정해요", href: `${base}/survey`, cta: "설문하러 가기" };
      if (!room.surveyClosedAt)
        return {
          title: `설문을 기다리는 중이에요 (${submitted} / ${room.members.length}명)`,
          desc: room.opinionDeadlineAt ? `${dateTimeLabel(room.opinionDeadlineAt)}까지 모아요` : "모두 내면 같이 정하기가 열려요",
          href: `${base}/survey`,
          cta: "설문 보기",
        };
      return myTurn.length > 0
        ? { title: `내 선택이 필요한 게 ${myTurn.length}개 있어요`, desc: "모두 같은 걸 골라야 정해져요", href: `${base}/vote`, cta: "정하러 가기" }
        : { title: "친구들이 고르는 중이에요", desc: "갈린 것만 남았어요. 의견을 남겨 보세요", href: `${base}/vote`, cta: "같이 정하기 보기" };
    case "PLANNING":
      // 일정을 짜는 중에도 직접 연 안건에 투표할 수 있다
      if (myTurn.length > 0)
        return { title: `내 선택이 필요한 게 ${myTurn.length}개 있어요`, desc: "모두 같은 걸 골라야 정해져요", href: `${base}/vote`, cta: "정하러 가기" };
      return { title: "가고 싶은 곳을 모아 일정을 짜요", desc: "모두 원하는 곳을 확정해서 일정에 넣어요", href: `${base}/places`, cta: "가고 싶은 곳 보기" };
    case "REVIEWING":
      return { title: "최종 일정에 동의해 주세요", desc: "모두 동의하면 일정이 잠기고 확정돼요", href: `${base}/schedule`, cta: "일정에서 보기" };
    case "FINALIZED":
      return { title: "우리 여행이 정해졌어요", desc: room.finalizedAt ? `${dateTimeLabel(room.finalizedAt)}에 확정됐어요` : "", href: `${base}/plan`, cta: "확정된 계획 보기" };
    case "EXPIRED":
      return { title: "이 방은 닫혔어요", desc: "설문 마감까지 아무도 답을 내지 않았어요" };
  }
}

export default function RoomHome() {
  const { roomId, room, myMember } = useRoom();
  const base = `/rooms/${roomId}`;
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const board = useApi(`board:${roomId}`, () => api.placeBoard(roomId), { live: true });
  const logs = useApi(`logs-home:${roomId}`, () => api.changeLogs(roomId, null, 5), { live: true });

  const myTurn = (agendas.data?.agendas ?? []).filter((a) => a.status === "VOTING" && a.eligible && a.myOptionId === null);
  const step = nextStep(room, Boolean(myMember?.surveySubmitted), myTurn, base);
  const confirmed = (board.data?.candidates ?? []).filter((c) => c.stage === "CONFIRMED");
  const inviteUrl = typeof window === "undefined" ? "" : `${window.location.origin}/invite/${room.inviteCode}`;

  return (
    <div className="stack" style={{ gap: 36, maxWidth: 960, width: "100%", margin: "0 auto" }}>
      <section aria-labelledby="trip-h" style={{ position: "relative", borderRadius: 28, overflow: "hidden" }}>
        <Cover destination={room.destination} style={{ height: 300, fontSize: 120, opacity: 0.9 }} />
        <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", justifyContent: "space-between", padding: "26px 30px 30px", color: "var(--on-accent)" }}>
          <span className="tag" style={{ background: "var(--surface)", color: "var(--ink)", alignSelf: "flex-start" }}>
            {dDay(room.startDate, room.endDate)}
          </span>
          <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-end", gap: 20 }}>
            <div>
              <h1 id="trip-h" style={{ margin: 0, fontSize: 44, lineHeight: 1.1 }}>
                {room.name}
              </h1>
              <p style={{ marginTop: 10, fontSize: 17 }}>
                {room.destination}, {dateLabel(room.startDate)}부터 {tripLength(room.startDate, room.endDate)}
              </p>
            </div>
            <span className="row" style={{ fontSize: 14 }}>
              <Faces names={room.members.map((m) => m.name)} />
              {room.members.length}명이 함께
            </span>
          </div>
        </div>
      </section>

      <section aria-labelledby="next-h" className="row" style={{ justifyContent: "space-between", gap: 16, marginTop: -16 }}>
        <div>
          <h2 id="next-h" style={{ margin: 0, fontSize: 24 }}>
            {step.title}
          </h2>
          {step.desc && <p className="muted" style={{ marginTop: 4 }}>{step.desc}</p>}
        </div>
        {step.href && (
          <Link className="btn btn-accent" href={step.href}>
            {step.cta}
          </Link>
        )}
      </section>

      {room.members.length <= 1 && room.status === "SURVEYING" && (
        <section className="card" aria-labelledby="invite-h">
          <h2 className="h3" id="invite-h">
            친구를 불러요
          </h2>
          <p className="small muted">링크를 보내면 친구가 들어와서 설문부터 시작해요. 초대 코드는 {room.inviteCode}예요</p>
          <CopyButton text={inviteUrl} label="초대 링크 복사" />
        </section>
      )}

      {confirmed.length > 0 && (
        <section aria-labelledby="pick-h">
          <div className="sec-h">
            <h2 id="pick-h">이미 정한 곳</h2>
            <Link href={`${base}/places`} style={{ fontSize: 14, textDecoration: "none" }}>
              가고 싶은 곳 {board.data?.candidates.length ?? 0}곳 보기
            </Link>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
            {confirmed.map((c) => (
              <Link key={c.placeId} href={`${base}/schedule`} className="card" style={{ textDecoration: "none", color: "var(--ink)", padding: "14px 16px", gap: 2 }}>
                <b>{c.name}</b>
                <span className="small muted">{c.address}</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="ppl-h">
        <div className="sec-h">
          <h2 id="ppl-h">{room.members.length}명은 지금</h2>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12 }}>
          {room.members.map((m) => (
            <div
              key={m.memberId}
              className="card"
              style={{
                flexDirection: "row",
                alignItems: "center",
                padding: "14px 16px",
                gap: 12,
                boxShadow: myMember?.memberId === m.memberId ? "inset 0 0 0 2px var(--accent)" : undefined,
              }}
            >
              <Avatar name={m.name} size="l" />
              <span className="stack" style={{ gap: 0, minWidth: 0 }}>
                <b>
                  {m.name}
                  {myMember?.memberId === m.memberId && " (나)"}
                </b>
                <span className="small muted">{m.surveySubmitted ? "설문을 냈어요" : "설문 전이에요"}</span>
              </span>
            </div>
          ))}
        </div>
      </section>

      <section aria-labelledby="news-h">
        <div className="sec-h">
          <h2 id="news-h">최근 소식</h2>
          <Link href={`${base}/news`} style={{ fontSize: 14, textDecoration: "none" }}>
            모두 보기
          </Link>
        </div>
        <div className="feed">
          {(logs.data?.items ?? []).map((it) => (
            <div key={it.logId}>
              {it.actor ? <Avatar name={it.actor.name} /> : <span className="av">착</span>}
              <span>{it.summary}</span>
              <span className="tm">{feedTime(it.createdAt)}</span>
            </div>
          ))}
          {logs.data && logs.data.items.length === 0 && <p className="muted">아직 소식이 없어요</p>}
        </div>
      </section>
    </div>
  );
}
