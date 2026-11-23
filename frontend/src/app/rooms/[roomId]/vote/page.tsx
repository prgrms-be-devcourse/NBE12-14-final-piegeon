"use client";

import Link from "next/link";
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
          {open.map((a) => (
            <AgendaCard key={a.agendaId} item={a} />
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
            {resolved.map((a) => (
              <div key={a.agendaId} className="row" style={{ flexWrap: "nowrap", gap: 12 }}>
                <span className="stamp">착</span>
                <span className="stack" style={{ gap: 0 }}>
                  <b>{a.resolvedOption?.label}</b>
                  <span className="small muted">{a.title.replace("을 정해주세요", "").replace("를 정해주세요", "")}</span>
                </span>
              </div>
            ))}
          </aside>
        )}
      </div>
    </>
  );
}
