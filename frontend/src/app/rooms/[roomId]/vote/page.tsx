"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AgendaCard } from "@/components/AgendaCard";
import { useRoom } from "@/components/room";
import { Loading, Notice } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { requestRefresh, useApi } from "@/lib/hooks";
import type { AgendaListItem } from "@/lib/types";

const MIN_OPTIONS = 2;
const MAX_OPTIONS = 5;

// 같은 주제의 회차를 한 줄로 묶는 키. 자유 안건은 주제가 모두 CUSTOM이라 제목까지 봐야 한다
const roundKey = (a: AgendaListItem) => `${a.subject}:${a.title}`;

export default function VotePage() {
  const { roomId, room } = useRoom();
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const list = agendas.data?.agendas ?? [];
  const open = list.filter((a) => a.status !== "RESOLVED");
  const resolved = list.filter((a) => a.status === "RESOLVED" && a.subject !== "FINAL_PLAN");
  // 주제별 가장 최근 회차. 이보다 앞선 미결 회차는 다음 회차 버튼을 막는다
  const latestRound = new Map<string, number>();
  for (const a of list) latestRound.set(roundKey(a), Math.max(latestRound.get(roundKey(a)) ?? 0, a.round));
  // 설문 항목 밖의 주제는 설문이 마감된 뒤(안건을 정하는 중이거나 일정을 짜는 중)부터 직접 안건으로 열 수 있다
  const canOpenCustom = (room.status === "SURVEYING" && Boolean(room.surveyClosedAt)) || room.status === "PLANNING";
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
          <p>설문에서 갈린 것과 직접 연 안건이 올라와요. 모두 같은 걸 골라야 정해지고, 누가 뭘 골랐는지 다 보여요</p>
        </div>
      </div>
      {agendas.loading && <Loading />}
      {agendas.error && <Notice kind="err">{agendas.error.message}</Notice>}
      {canOpenCustom && <CustomAgendaForm roomId={roomId} titles={list.filter((a) => a.subject === "CUSTOM").map((a) => a.title)} />}
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
            <AgendaCard key={a.agendaId} item={a} latestRound={latestRound.get(roundKey(a))} />
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
                    {/* 설문 안건의 기본 제목만 줄인다. 자유 안건 제목은 멤버가 쓴 그대로 둔다 */}
                    {a.subject === "CUSTOM" ? a.title : a.title.replace("을 정해주세요", "").replace("를 정해주세요", "")}
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

/**
 * 설문 항목 밖의 주제로 안건 열기 (V-09). 제목과 선지 2~5개를 적는다.
 *
 * @param titles 이미 있는 자유 안건 제목. 같은 제목은 서버가 막지만, 보내기 전에 미리 알려 준다
 */
function CustomAgendaForm({ roomId, titles }: { roomId: number | string; titles: string[] }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [options, setOptions] = useState<string[]>(["", ""]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setTitle("");
    setOptions(["", ""]);
    setError(null);
    setOpen(false);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const t = title.trim();
    const opts = options.map((o) => o.trim());
    if (titles.includes(t)) {
      setError("같은 제목의 안건이 이미 있어요. 못 정했다면 그 안건에서 다음 회차를 열어 주세요.");
      return;
    }
    if (new Set(opts).size !== opts.length) {
      setError("같은 선지가 두 번 들어갔어요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createAgenda(roomId, { title: t, options: opts });
      reset();
      // 새 안건은 목록을 다시 불러와 카드로 생긴다
      requestRefresh([]);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <div className="row">
        <button type="button" className="btn btn-line btn-sm" onClick={() => setOpen(true)}>
          다른 것도 같이 정하기
        </button>
        <span className="small muted">설문에 없던 것도 안건으로 올려 투표할 수 있어요</span>
      </div>
    );
  }

  return (
    <form className="card stack" style={{ gap: 12 }} onSubmit={onSubmit} aria-labelledby="custom-h">
      <h2 className="h3" id="custom-h">
        같이 정할 것 올리기
      </h2>
      <div className="f">
        <label htmlFor="custom-title">무엇을 정할까요?</label>
        <input
          id="custom-title"
          className="in"
          maxLength={50}
          placeholder="예: 렌터카 빌릴까?"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
      </div>
      <fieldset className="stack" style={{ gap: 8, border: 0, padding: 0, margin: 0 }}>
        <legend className="small">
          선지 ({MIN_OPTIONS}~{MAX_OPTIONS}개)
        </legend>
        {options.map((o, i) => (
          <div key={i} className="row">
            <label htmlFor={`custom-option-${i}`} className="sr-only">
              선지 {i + 1}
            </label>
            <input
              id={`custom-option-${i}`}
              className="in grow"
              maxLength={50}
              placeholder={`선지 ${i + 1}`}
              value={o}
              onChange={(e) => setOptions(options.map((v, j) => (j === i ? e.target.value : v)))}
              required
            />
            {options.length > MIN_OPTIONS && (
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                aria-label={`선지 ${i + 1} 빼기`}
                onClick={() => setOptions(options.filter((_, j) => j !== i))}
              >
                빼기
              </button>
            )}
          </div>
        ))}
        {options.length < MAX_OPTIONS && (
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            style={{ alignSelf: "flex-start" }}
            onClick={() => setOptions([...options, ""])}
          >
            선지 추가
          </button>
        )}
      </fieldset>
      <p className="small muted" style={{ margin: 0 }}>
        모두 같은 걸 고르면 정해져요. 연 뒤에는 제목과 선지를 바꿀 수 없어요
      </p>
      {error && <Notice kind="err">{error}</Notice>}
      <div className="row">
        <button type="submit" className="btn btn-accent btn-sm" disabled={busy || !title.trim()}>
          투표 열기
        </button>
        <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={reset}>
          취소
        </button>
      </div>
    </form>
  );
}
