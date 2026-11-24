"use client";

import Link from "next/link";
import { useRoom } from "@/components/room";
import { Faces, Loading, Notice } from "@/components/ui";
import { api } from "@/lib/api";
import { dateTimeLabel, shortDate, tripLength } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { AgendaSubject } from "@/lib/types";

export default function PlanPage() {
  const { roomId, room } = useRoom();
  const board = useApi(`schedule:${roomId}`, () => api.schedule(roomId), { live: true });
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });

  // 같은 주제는 가장 최근 회차가 앞에 온다
  const decided = (subject: AgendaSubject) =>
    agendas.data?.agendas.find((a) => a.subject === subject && a.status === "RESOLVED")?.resolvedOption?.label ?? "미정";
  const finalized = room.status === "FINALIZED";
  const itemCount = (board.data?.days ?? []).reduce((n, d) => n + d.items.length, 0);

  return (
    <>
      <div className="head">
        <div>
          <h1>{finalized ? "우리 여행이 정해졌어요" : "확정되면 이렇게 보여요"}</h1>
          <p>{finalized && room.finalizedAt ? `${dateTimeLabel(room.finalizedAt)}, 모두 동의했어요` : "아직 확정 전이에요. 지금까지 짠 일정을 미리 보여 줘요"}</p>
        </div>
        {!finalized && (
          <Link href={`/rooms/${roomId}/schedule`} className="btn btn-line">
            일정으로 돌아가기
          </Link>
        )}
      </div>
      {(board.loading || agendas.loading) && <Loading />}
      {board.error && <Notice kind="err">{board.error.message}</Notice>}
      {board.data && (
        <article className="pass" aria-label="확정된 여행 계획">
          <div className="pass-top">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <b style={{ fontSize: 20 }}>착착 BOARDING PASS</b>
              <span className="tag" style={{ background: "var(--surface)", color: "var(--ink)" }}>
                {finalized ? "확정" : "확정 전"}
              </span>
            </div>
            <h2 style={{ margin: 0, fontSize: 32 }}>{room.name}</h2>
            <div className="route">
              <span className="stack" style={{ gap: 2 }}>
                <b>{room.destination}</b>
                <span>
                  {shortDate(room.startDate)} 출발, {shortDate(room.endDate)} 귀가
                </span>
              </span>
            </div>
          </div>
          <div className="pass-facts">
            <span>
              PASSENGERS<b>{room.members.length}명</b>
            </span>
            <span>
              DURATION<b>{tripLength(room.startDate, room.endDate)}</b>
            </span>
            <span>
              STAY<b>{decided("STAY")}</b>
            </span>
            <span>
              PACE<b>{decided("PACE")}</b>
            </span>
          </div>
          <div className="pass-days">
            {board.data.days.map((d) => (
              <div key={d.scheduleDayId}>
                <h3>
                  DAY {d.dayIndex} <span className="muted" style={{ fontWeight: 400 }}>{shortDate(d.date)}</span>
                </h3>
                {d.items.length === 0 ? (
                  <p className="small muted">자유 일정</p>
                ) : (
                  <ul>
                    {d.items.map((it) => (
                      <li key={it.itemId}>
                        <span className="t">{it.startTime ?? "-"}</span>
                        <span>
                          {it.name}
                          {it.memo && <span className="small muted">, {it.memo}</span>}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
          </div>
          <div className="row" style={{ padding: "16px 30px 24px", borderTop: "1px dashed var(--line)", justifyContent: "space-between" }}>
            <span className="row">
              <Faces names={room.members.map((m) => m.name)} />
              <span className="small muted">{room.members.map((m) => m.name).join(", ")}이 함께 정한 여행</span>
            </span>
            <span className="small muted">
              일정 {itemCount}개, 1인 예산 {decided("BUDGET")}
            </span>
          </div>
        </article>
      )}
    </>
  );
}
