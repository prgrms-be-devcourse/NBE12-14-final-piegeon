"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AgendaCard } from "@/components/AgendaCard";
import { useRoom } from "@/components/room";
import { Loading, Notice } from "@/components/ui";
import { api } from "@/lib/api";
import { useApi } from "@/lib/hooks";

export default function VotePage() {
  const { roomId, room } = useRoom();
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const list = agendas.data?.agendas ?? [];
  const open = list.filter((a) => a.status !== "RESOLVED");
  const resolved = list.filter((a) => a.status === "RESOLVED" && a.subject !== "FINAL_PLAN");
  // 주제별 가장 최근 회차. 이보다 앞선 미결 회차는 다음 회차 버튼을 막는다
  const latestRound = new Map<string, number>();
  for (const a of list) latestRound.set(a.subject, Math.max(latestRound.get(a.subject) ?? 0, a.round));
  // 이미 맞춰진 것 중 펼쳐 본 안건. 그때 나눈 의견과 누가 뭘 골랐는지 다시 볼 수 있다
  const [viewId, setViewId] = useState<number | null>(null);
  const viewing = resolved.find((a) => a.agendaId === viewId);
  const viewRef = useRef<HTMLDivElement>(null);

  // 좁은 화면에서는 목록이 아래에 있어 펼친 카드가 화면 밖에 뜨므로 그쪽으로 옮긴다
  useEffect(() => {
    if (viewId !== null) viewRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [viewId]);

  return (
    <>
      <div className="head">
        <div>
          <h1>같이 정하기</h1>
          <p>설문에서 갈린 것만 남았어요. 모두 같은 걸 골라야 정해지고, 누가 뭘 골랐는지 다 보여요</p>
        </div>
      </div>
      {agendas.loading && <Loading />}
      {agendas.error && <Notice kind="err">{agendas.error.message}</Notice>}
      {agendas.data && list.length === 0 && (
        <div className="card empty">
          {room.status === "SURVEYING" ? (
            <>
              <b>아직 정할 게 없어요</b>
              <span>모두 설문을 내면, 답이 갈린 것만 여기에 투표로 올라와요</span>
              <Link href={`/rooms/${roomId}/survey`} className="btn btn-accent">
                설문 보러 가기
              </Link>
            </>
          ) : (
            <b>정할 안건이 없어요</b>
          )}
        </div>
      )}
      <div className="with-panel" style={{ alignItems: "stretch" }}>
        <div className="stack grow" style={{ gap: 18 }}>
          {viewing && (
            <div ref={viewRef} className="stack" style={{ gap: 8 }}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="small muted">이미 맞춰진 것 다시 보기</span>
                <button type="button" className="btn btn-ghost" onClick={() => setViewId(null)}>
                  닫기
                </button>
              </div>
              <AgendaCard key={viewing.agendaId} item={viewing} />
            </div>
          )}
          {open.map((a) => (
            <AgendaCard key={a.agendaId} item={a} latestRound={latestRound.get(a.subject)} />
          ))}
          {agendas.data && open.length === 0 && list.length > 0 && (
            <div className="card empty">
              <b>갈렸던 건 다 정해졌어요</b>
              {room.status === "PLANNING" && (
                <Link href={`/rooms/${roomId}/places`} className="btn btn-accent">
                  가고 싶은 곳 모으러 가기
                </Link>
              )}
            </div>
          )}
        </div>
        {resolved.length > 0 && (
          <aside className="card" style={{ flex: "0 0 300px", alignSelf: "flex-start" }} aria-labelledby="ok-h">
            <h2 className="h3" id="ok-h">
              이미 맞춰진 것
            </h2>
            <p className="small muted" style={{ margin: 0 }}>
              누르면 그때 나눈 의견과 누가 뭘 골랐는지 볼 수 있어요
            </p>
            {resolved.map((a) => (
              <button
                key={a.agendaId}
                type="button"
                className="tile"
                style={{ flexDirection: "row", alignItems: "center", justifyContent: "flex-start", gap: 12 }}
                aria-pressed={viewId === a.agendaId}
                onClick={() => setViewId(viewId === a.agendaId ? null : a.agendaId)}
              >
                <span className="stamp">착</span>
                <span className="stack" style={{ gap: 0 }}>
                  <b>{a.resolvedOption?.label}</b>
                  <small>
                    {a.title.replace("을 정해주세요", "").replace("를 정해주세요", "")}
                    {a.round > 1 && `, ${a.round}회차`}
                  </small>
                </span>
              </button>
            ))}
          </aside>
        )}
      </div>
    </>
  );
}
