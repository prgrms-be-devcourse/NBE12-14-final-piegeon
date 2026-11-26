"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AgendaCard, CommentList } from "@/components/AgendaCard";
import { useConfirm } from "@/components/ConfirmDialog";
import { useRoom } from "@/components/room";
import { Loading, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { shortDate, weekday, withObject } from "@/lib/format";
import { requestRefresh, topics, useApi } from "@/lib/hooks";
import type { AgendaListItem, DayChangeResponse, DaySnapshot, PlaceCandidateItem, ScheduleBoardResponse, ScheduleItemResponse } from "@/lib/types";

const ITEMS_PER_DAY = 10;

export default function SchedulePage() {
  const { roomId, room, reloadRoom } = useRoom();
  const board = useApi(`schedule:${roomId}`, () => api.schedule(roomId), { live: true });
  const places = useApi(`board:${roomId}`, () => api.placeBoard(roomId), { live: true });
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const [dayIndex, setDayIndex] = useState(1);
  const [notice, setNotice] = useState<{ kind: "err" | "warn" | "ok"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const { confirm, dialog } = useConfirm();

  const status = board.data?.roomStatus ?? room.status;
  const editable = status === "PLANNING";
  const days = board.data?.days ?? [];
  const day = days.find((d) => d.dayIndex === dayIndex) ?? days[0];
  const pool = (places.data?.candidates ?? []).filter((c) => c.stage === "CONFIRMED");
  const finals = (agendas.data?.agendas ?? []).filter((a) => a.subject === "FINAL_PLAN");
  const finalAgenda = finals.find((a) => a.status === "VOTING");
  // 가장 최근 최종 확정 투표가 반대나 마감으로 끝났으면, 일정을 고칠 때 그 이유를 볼 수 있게 남긴다
  const lastFinal = finals.reduce<AgendaListItem | undefined>((m, a) => (!m || a.agendaId > m.agendaId ? a : m), undefined);
  const turnedDown = lastFinal && lastFinal.status !== "VOTING" && lastFinal.resolvedOption?.value !== "AGREE" ? lastFinal : undefined;

  /**
   * 블록을 바꾸는 요청. 성공하면 바뀐 날만 갈아 끼우고, 버전이 낡았으면 보드를 새로 불러온다.
   * 성공했는지 돌려줘서, 실패했을 때 입력한 시간·메모를 지우지 않게 한다
   */
  async function change(action: () => Promise<DayChangeResponse | { day: DaySnapshot }>): Promise<boolean> {
    if (!board.data) return false;
    setBusy(true);
    setNotice(null);
    try {
      const res = await action();
      const next: ScheduleBoardResponse = {
        ...board.data,
        days: board.data.days.map((d) => (d.scheduleDayId === res.day.scheduleDayId ? res.day : d)),
      };
      board.setData(next);
      if ("warnings" in res && res.warnings?.includes("TIME_ORDER_MISMATCH")) {
        setNotice({ kind: "warn", text: "이 블록의 시간이 앞뒤 블록과 순서가 맞지 않아요. 저장은 됐어요." });
      }
      requestRefresh();
      return true;
    } catch (e) {
      if (e instanceof ApiError && e.code === "SCHEDULE_VERSION_CONFLICT") {
        await board.reload();
        setNotice({ kind: "warn", text: "다른 친구가 먼저 이 날을 고쳐서 새로 불러왔어요. 다시 해 주세요." });
      } else {
        setNotice({ kind: "err", text: errorMessage(e) });
      }
      return false;
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="head">
        <div>
          <h1>일정</h1>
          <p>하루 10칸까지 시간, 확정된 장소, 메모를 넣어요. 날짜별로 따로 확정하지 않고, 모두 동의해서 한 번에 확정해요</p>
        </div>
      </div>

      {editable && turnedDown && <TurnedDown agenda={turnedDown} />}

      <FinalReview
        status={status}
        finalAgenda={finalAgenda ?? null}
        dayCounts={days.map((d) => ({ dayIndex: d.dayIndex, count: d.items.length }))}
        solo={room.members.length === 1}
        onOpened={async () => {
          await reloadRoom();
          await agendas.reload();
          await board.reload();
        }}
      />

      {board.loading && <Loading />}
      {board.error && <Notice kind="err">{board.error.message}</Notice>}
      {notice && <Notice kind={notice.kind}>{notice.text}</Notice>}

      {days.length > 0 && day && (
        <>
          <nav className="dsel" aria-label="날짜 고르기">
            {days.map((d) => (
              <button key={d.scheduleDayId} type="button" className="cd" aria-pressed={d.dayIndex === day.dayIndex} onClick={() => setDayIndex(d.dayIndex)}>
                <span className="ph">
                  <b>{Number(d.date.slice(8, 10))}</b>
                  <span>{weekday(d.date)}</span>
                </span>
                <span className="tx">
                  <b>{d.dayIndex}일차</b>
                  <span>
                    {shortDate(d.date)}, {d.items.length} / {ITEMS_PER_DAY}칸
                  </span>
                </span>
              </button>
            ))}
          </nav>

          <section className="card" aria-labelledby="day-h">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2 className="h2" id="day-h">
                {day.dayIndex}일차 <span className="small muted" style={{ fontWeight: 400 }}>{shortDate(day.date)}</span>
              </h2>
              <span className="small muted">
                {day.items.length} / {ITEMS_PER_DAY}칸
              </span>
            </div>
            {day.items.length === 0 && (
              <div className="empty">
                <b>아직 넣은 곳이 없어요</b>
                <span>&apos;다 같이 가고 싶어요&apos;에 모인 곳을 확정하면 여기에 넣을 수 있어요</span>
              </div>
            )}
            <div>
              {day.items.map((it, i) => (
                <Slot
                  key={it.itemId}
                  item={it}
                  editable={editable}
                  busy={busy}
                  canUp={i > 0}
                  canDown={i < day.items.length - 1}
                  onUp={() => change(() => api.moveItem(roomId, it.itemId, i >= 2 ? day.items[i - 2].itemId : null, day.version))}
                  onDown={() => change(() => api.moveItem(roomId, it.itemId, day.items[i + 1].itemId, day.version))}
                  onSave={(time, memo) => change(() => api.updateItem(roomId, it.itemId, time, memo, day.version))}
                  onDelete={async () => {
                    const ok = await confirm({
                      title: `${withObject(it.name)} ${day.dayIndex}일차에서 뺄까요?`,
                      body: "장소는 확정된 곳에 그대로 남아서 다시 넣을 수 있어요.",
                      confirmLabel: "빼기",
                    });
                    if (!ok) return;
                    void change(() => api.deleteItem(roomId, it.itemId, day.version));
                  }}
                />
              ))}
            </div>
            {editable && (
              <AddItem
                day={day}
                pool={pool}
                busy={busy}
                onAdd={(placeId, afterItemId, time, memo) =>
                  change(() => api.addItem(roomId, day.scheduleDayId, { placeId, afterItemId, startTime: time, memo, dayVersion: day.version }))
                }
              />
            )}
          </section>
        </>
      )}

      {dialog}

      {editable && places.data && pool.length === 0 && (
        <div className="card empty">
          <b>넣을 수 있는 곳이 아직 없어요</b>
          <span>가고 싶은 곳에서 모두 원하는 곳을 확정하면 일정에 넣을 수 있어요</span>
          <Link href={`/rooms/${roomId}/places`} className="btn btn-accent">
            가고 싶은 곳 보러 가기
          </Link>
        </div>
      )}
    </>
  );
}

function FinalReview({
  status,
  finalAgenda,
  dayCounts,
  solo,
  onOpened,
}: {
  status: string;
  finalAgenda: import("@/lib/types").AgendaListItem | null;
  /** 아무것도 넣지 않은 날의 일차. 서버는 빈 날이 있어도 투표를 열어 준다 */
  dayCounts: { dayIndex: number; count: number }[];
  /** 활동 멤버가 나 혼자인지. 혼자면 여는 사람의 동의만으로 바로 확정된다 */
  solo: boolean;
  onOpened: () => Promise<void>;
}) {
  const { roomId } = useRoom();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { confirm, dialog, cancel } = useConfirm();

  // 확인창이 떠 있는 동안 다른 멤버가 먼저 투표를 열면 화면이 투표 중으로 바뀌어 확인창이 안 보이게 된다.
  // 이때 닫아 두지 않으면, 반대가 나와 다시 일정을 고치는 화면으로 돌아올 때 예전 확인창이 저절로 다시 뜬다
  useEffect(() => {
    if (status !== "PLANNING") cancel();
  }, [status, cancel]);

  if (status === "REVIEWING" && finalAgenda) {
    return (
      <div className="stack">
        <Notice kind="warn">최종 확정 투표가 열려 있어서 일정을 고칠 수 없어요. 아래 일정을 보고 동의하거나 반대해 주세요.</Notice>
        <AgendaCard item={finalAgenda} />
      </div>
    );
  }
  if (status === "FINALIZED") {
    return (
      <Notice kind="ok">
        모두 동의해서 일정이 확정됐어요. <Link href={`/rooms/${roomId}/plan`}>확정된 계획 보기</Link>
      </Notice>
    );
  }
  if (status !== "PLANNING") return null;
  return (
    <section className="card" style={{ flexDirection: "row", alignItems: "center", flexWrap: "wrap", justifyContent: "space-between" }}>
      <div className="stack" style={{ gap: 2 }}>
        <b>일정을 다 짰나요?</b>
        <span className="small muted">최종 확정 투표를 열면 일정이 잠기고, 모두 동의하면 확정돼요. 한 명이라도 반대하면 다시 고칠 수 있어요</span>
      </div>
      <button
        type="button"
        className="btn btn-accent"
        disabled={busy}
        onClick={async () => {
          // 투표를 연 사람은 서버가 동의로 넣는다. 그래서 혼자인 방은 열자마자 확정된다
          const notes = solo ? ["지금 방에 나 혼자라서 열자마자 일정이 확정돼요. 확정하면 되돌릴 수 없어요."] : [];
          // 서버는 빈 날이 있어도 투표를 열어 준다. 빈 날이 있으면 일차별로 몇 곳 넣었는지 보여 줘서 열기 전에 알아채게 한다
          const hasEmptyDay = dayCounts.some((d) => d.count === 0);
          const ok = await confirm({
            title: "최종 확정 투표를 열까요?",
            body: "투표 중에는 일정을 고칠 수 없어요. 투표를 연 나는 동의한 걸로 들어가요.",
            notes,
            extra:
              hasEmptyDay ? (
                <div className="cf-days" aria-label="일차별로 넣은 곳">
                  {dayCounts.map((d) => (
                    <span key={d.dayIndex} className={d.count === 0 ? "cf-day cf-empty" : "cf-day"}>
                      <b>{d.dayIndex}일차</b>
                      <span>{d.count === 0 ? "비어 있음" : `${d.count}곳`}</span>
                    </span>
                  ))}
                </div>
              ) : undefined,
            confirmLabel: solo ? "열고 확정하기" : "투표 열기",
          });
          if (!ok) return;
          setBusy(true);
          setError(null);
          try {
            await api.openFinalReview(roomId);
            await onOpened();
            requestRefresh();
          } catch (e) {
            setError(errorMessage(e));
          } finally {
            setBusy(false);
          }
        }}
      >
        최종 확정 투표 열기
      </button>
      {error && <Notice kind="err">{error}</Notice>}
      {dialog}
    </section>
  );
}

/** 반대나 마감으로 끝난 지난 최종 확정 투표. 반대 이유는 그 안건의 의견으로 남아 있다 */
function TurnedDown({ agenda }: { agenda: AgendaListItem }) {
  const { roomId } = useRoom();
  const detail = useApi(`agenda:${roomId}:${agenda.agendaId}`, () => api.agenda(roomId, agenda.agendaId), {
    live: true,
    topic: topics.agenda(agenda.agendaId),
  });
  const comments = useApi(`agenda-comments:${roomId}:${agenda.agendaId}`, () => api.agendaComments(roomId, agenda.agendaId), {
    live: true,
    topic: topics.agendaComments(agenda.agendaId),
  });
  const rejected = agenda.status === "RESOLVED";
  const rejecters = detail.data?.options.find((o) => o.value === "REJECT")?.voters ?? [];
  const list = comments.data?.comments ?? [];

  return (
    <section className="card" aria-labelledby="turned-down-h">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2 className="h3" id="turned-down-h">
          {rejected ? "지난 최종 확정 투표에서 반대가 나왔어요" : "지난 최종 확정 투표가 모두 동의하지 못한 채 마감됐어요"}
        </h2>
        {rejected ? (
          rejecters.length > 0 && <span className="tag">반대: {rejecters.map((v) => v.name).join(", ")}</span>
        ) : (
          <span className="tag n">마감됐어요</span>
        )}
      </div>
      <p className="small muted" style={{ margin: 0 }}>
        남긴 의견을 보고 일정을 고친 뒤, 아래에서 최종 확정 투표를 다시 열어 주세요
      </p>
      {comments.loading && <p className="small muted">불러오는 중이에요</p>}
      {comments.error && <Notice kind="err">{comments.error.message}</Notice>}
      {comments.data && list.length === 0 && <p className="small muted">남긴 의견이 없어요</p>}
      {list.length > 0 && (
        <div className="stack" style={{ paddingTop: 14, borderTop: "1px solid var(--line)" }}>
          <CommentList comments={list} />
        </div>
      )}
    </section>
  );
}

function Slot({
  item,
  editable,
  busy,
  canUp,
  canDown,
  onUp,
  onDown,
  onSave,
  onDelete,
}: {
  item: ScheduleItemResponse;
  editable: boolean;
  busy: boolean;
  canUp: boolean;
  canDown: boolean;
  onUp: () => void;
  onDown: () => void;
  onSave: (time: string | null, memo: string | null) => Promise<boolean>;
  onDelete: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [time, setTime] = useState(item.startTime ?? "");
  const [memo, setMemo] = useState(item.memo ?? "");

  if (editing) {
    return (
      <form
        className="slot"
        onSubmit={async (e) => {
          e.preventDefault();
          // 실패하면 고치던 창을 그대로 둬서 같은 값으로 다시 저장할 수 있게 한다
          if (await onSave(time || null, memo.trim() || null)) setEditing(false);
        }}
      >
        <div className="stack grow" style={{ gap: 8 }}>
          <b>{item.name}</b>
          <div className="row">
            <label className="f" style={{ width: 140 }}>
              <span className="lb small">시간</span>
              <input className="in" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
            </label>
            <label className="f grow" style={{ minWidth: 200 }}>
              <span className="lb small">메모</span>
              <input className="in" maxLength={200} value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 점심, 예약 12시" />
            </label>
          </div>
          <div className="row">
            <button type="submit" className="btn btn-accent btn-sm" disabled={busy}>
              저장
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(false)}>
              그만두기
            </button>
          </div>
        </div>
      </form>
    );
  }

  return (
    <div className="slot">
      <span className={item.startTime ? "tm" : "tm none"}>{item.startTime ?? "시간 미정"}</span>
      <span className="stack grow" style={{ gap: 2 }}>
        <b>{item.name}</b>
        <span className="memo">{item.memo || item.address}</span>
      </span>
      {editable && (
        <span className="acts">
          <button type="button" className="btn btn-ghost" disabled={busy || !canUp} onClick={onUp} aria-label={`${item.name} 위로`}>
            위로
          </button>
          <button type="button" className="btn btn-ghost" disabled={busy || !canDown} onClick={onDown} aria-label={`${item.name} 아래로`}>
            아래로
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={busy}
            onClick={() => {
              setTime(item.startTime ?? "");
              setMemo(item.memo ?? "");
              setEditing(true);
            }}
          >
            고치기
          </button>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={onDelete} aria-label={`${item.name} 빼기`}>
            빼기
          </button>
        </span>
      )}
    </div>
  );
}

function AddItem({
  day,
  pool,
  busy,
  onAdd,
}: {
  day: DaySnapshot;
  pool: PlaceCandidateItem[];
  busy: boolean;
  onAdd: (placeId: number, afterItemId: number | null, time: string | null, memo: string | null) => Promise<boolean>;
}) {
  const [placeId, setPlaceId] = useState("");
  // "end"면 맨 뒤, "start"면 맨 앞, 숫자면 그 블록 뒤
  const [after, setAfter] = useState("end");
  const [time, setTime] = useState("");
  const [memo, setMemo] = useState("");
  const left = ITEMS_PER_DAY - day.items.length;

  if (left <= 0) return <p className="small muted">이 날은 10칸이 꽉 찼어요</p>;
  if (pool.length === 0) return null;

  return (
    <form
      className="stack"
      style={{ paddingTop: 14, borderTop: "1px solid var(--line)", gap: 10 }}
      onSubmit={async (e) => {
        e.preventDefault();
        if (!placeId) return;
        const last = day.items[day.items.length - 1];
        const afterItemId = after === "start" ? null : after === "end" ? (last?.itemId ?? null) : Number(after);
        // 실패하면 고른 장소와 입력한 시간·메모를 남겨 둔다
        if (!(await onAdd(Number(placeId), afterItemId, time || null, memo.trim() || null))) return;
        setPlaceId("");
        setTime("");
        setMemo("");
        setAfter("end");
      }}
    >
      <b>빈 칸에 넣기, {left}칸 남음</b>
      <div className="row" style={{ alignItems: "flex-end" }}>
        <label className="f grow" style={{ minWidth: 180 }}>
          <span className="lb small">확정된 곳</span>
          <select className="in" value={placeId} onChange={(e) => setPlaceId(e.target.value)} required>
            <option value="">고르기</option>
            {pool.map((p) => (
              <option key={p.placeId} value={p.placeId}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label className="f" style={{ minWidth: 160 }}>
          <span className="lb small">자리</span>
          <select className="in" value={after} onChange={(e) => setAfter(e.target.value)}>
            <option value="end">맨 뒤</option>
            <option value="start">맨 앞</option>
            {day.items.slice(0, -1).map((it) => (
              <option key={it.itemId} value={it.itemId}>
                {it.name} 뒤
              </option>
            ))}
          </select>
        </label>
        <label className="f" style={{ width: 130 }}>
          <span className="lb small">시간 (없어도 돼요)</span>
          <input className="in" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
        <label className="f grow" style={{ minWidth: 180 }}>
          <span className="lb small">메모 (없어도 돼요)</span>
          <input className="in" maxLength={200} value={memo} onChange={(e) => setMemo(e.target.value)} />
        </label>
        <button type="submit" className="btn btn-accent" disabled={busy || !placeId}>
          넣기
        </button>
      </div>
    </form>
  );
}
