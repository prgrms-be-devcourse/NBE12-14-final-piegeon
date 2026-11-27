"use client";

import Link from "next/link";
import { Fragment, useEffect, useRef, useState } from "react";
import { AgendaCard, CommentList } from "@/components/AgendaCard";
import { CategoryIcon } from "@/components/CategoryIcon";
import { useConfirm } from "@/components/ConfirmDialog";
import { useRoom } from "@/components/room";
import { Loading, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { longDate, weekday, withObject } from "@/lib/format";
import { requestRefresh, topics, useApi } from "@/lib/hooks";
import type {
  AgendaListItem,
  DayChangeResponse,
  DaySnapshot,
  PlaceCandidateItem,
  PlaceCategory,
  ScheduleBoardResponse,
  ScheduleItemResponse,
  SwapDayResponse,
} from "@/lib/types";

const ITEMS_PER_DAY = 10;
const VIEW_KEY = "chakchak:schedule-view";

// 블록 오른쪽에 붙이는 장소 종류 이름. 서버가 주는 이름("명소·문화탐방" 등)은 꼬리표로 쓰기에 길어서 줄인 말을 따로 둔다
const CATEGORY_TAG: Record<PlaceCategory, string> = { FOOD: "맛집", NATURE: "자연", ACTIVITY: "체험", SHOPPING: "쇼핑", CULTURE: "명소" };

// "넣을 수 있는 곳"을 나누는 칸의 순서. 종류를 알 수 없는 곳은 맨 끝의 기타 칸에 모은다
const POOL_GROUPS: (PlaceCategory | "ETC")[] = ["FOOD", "CULTURE", "NATURE", "ACTIVITY", "SHOPPING", "ETC"];

/** 앞 블록에서 이 블록까지 얼마나 뒤인지. 예: "3시간 뒤", "1시간 30분 뒤". 둘 중 하나라도 시간이 없거나 순서가 거꾸로면 적지 않는다 */
function gapLabel(prev: string | null, next: string | null): string | null {
  if (!prev || !next) return null;
  const [ph, pm] = prev.split(":").map(Number);
  const [nh, nm] = next.split(":").map(Number);
  const minutes = nh * 60 + nm - (ph * 60 + pm);
  if (minutes <= 0) return null;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? (m > 0 ? `${h}시간 ${m}분 뒤` : `${h}시간 뒤`) : `${m}분 뒤`;
}

/** 지금 끌고 있는 것. 일정 블록이거나, '넣을 수 있는 곳'의 장소다 */
type Drag =
  | { kind: "item"; item: ScheduleItemResponse; fromDayId: number }
  | { kind: "place"; place: PlaceCandidateItem }
  // 날짜 카드. 다른 날짜 카드에 놓으면 두 날의 일정이 통째로 바뀐다
  | { kind: "day"; day: DaySnapshot };

export default function SchedulePage() {
  const { roomId, room, reloadRoom } = useRoom();
  const board = useApi(`schedule:${roomId}`, () => api.schedule(roomId), { live: true });
  const places = useApi(`board:${roomId}`, () => api.placeBoard(roomId), { live: true });
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const [dayIndex, setDayIndex] = useState(1);
  const [notice, setNotice] = useState<{ kind: "err" | "warn" | "ok"; text: React.ReactNode } | null>(null);
  const [busy, setBusy] = useState(false);
  const { confirm, dialog } = useConfirm();
  // 고치는 상태로 열린 블록. 한 번에 하나만 연다
  const [editingId, setEditingId] = useState<number | null>(null);
  // 넣기 폼이 열린 날
  const [addingDayId, setAddingDayId] = useState<number | null>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  // 끌던 것이 어느 날의 몇 번째 자리에 놓일지 (0이면 맨 앞)
  const [dropAt, setDropAt] = useState<{ dayId: number; index: number } | null>(null);
  const [overDayId, setOverDayId] = useState<number | null>(null);
  // "day"는 날짜를 골라 하루씩, "all"은 모든 날을 나란히 놓고 한눈에 본다
  const [view, setView] = useState<"day" | "all">("day");
  const dragTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // 고른 보기 방식은 이 브라우저에 기억해 둔다. 저장소를 못 쓰는 환경이면 기본값(하루씩)으로 둔다
  useEffect(() => {
    try {
      if (localStorage.getItem(VIEW_KEY) === "all") setView("all");
    } catch {}
  }, []);

  function pickView(next: "day" | "all") {
    setNotice(null);
    setView(next);
    setEditingId(null);
    setAddingDayId(null);
    try {
      localStorage.setItem(VIEW_KEY, next);
    } catch {}
  }

  const status = board.data?.roomStatus ?? room.status;
  const editable = status === "PLANNING";
  const days = board.data?.days ?? [];
  // 하루짜리 여행에는 보기 방식 버튼이 없다. 다른 방에서 '전체'를 골라 둔 채로 들어와도 갇히지 않게 '하루씩'으로 본다
  const shownView = days.length > 1 ? view : "day";

  // 다른 멤버가 최종 확정 투표를 열어 일정이 잠기면 열어 둔 것을 닫는다.
  // 닫지 않으면 반대가 나와 다시 고칠 수 있게 됐을 때 예전에 열어 둔 창이 저절로 다시 열린다
  useEffect(() => {
    if (editable) return;
    setEditingId(null);
    setAddingDayId(null);
    // 끌고 있던 중이었다면 그것도 끝낸다. 잠긴 방에 놓으면 서버가 거절하는 요청만 나간다
    clearTimeout(dragTimer.current);
    setDrag(null);
    setDropAt(null);
    setOverDayId(null);
  }, [editable]);
  const day = days.find((d) => d.dayIndex === dayIndex) ?? days[0];
  const pool = (places.data?.candidates ?? []).filter((c) => c.stage === "CONFIRMED");
  // 일정 응답에는 장소 종류가 없어서, 같이 불러온 장소 목록에서 찾는다
  const categoryOf = new Map<number, PlaceCategory | null>((places.data?.candidates ?? []).map((c) => [c.placeId, c.category?.value ?? null]));
  const finals = (agendas.data?.agendas ?? []).filter((a) => a.subject === "FINAL_PLAN");
  const finalAgenda = finals.find((a) => a.status === "VOTING");
  // 가장 최근 최종 확정 투표가 반대나 마감으로 끝났으면, 일정을 고칠 때 그 이유를 볼 수 있게 남긴다
  const lastFinal = finals.reduce<AgendaListItem | undefined>((m, a) => (!m || a.agendaId > m.agendaId ? a : m), undefined);
  const turnedDown = lastFinal && lastFinal.status !== "VOTING" && lastFinal.resolvedOption?.value !== "AGREE" ? lastFinal : undefined;

  /**
   * 블록을 바꾸는 요청. 성공하면 바뀐 날만 갈아 끼우고, 버전이 낡았으면 보드를 새로 불러온다.
   * 성공했는지 돌려줘서, 실패했을 때 입력한 시간·메모를 지우지 않게 한다
   */
  async function change(action: () => Promise<DayChangeResponse | { day: DaySnapshot } | SwapDayResponse>): Promise<boolean> {
    if (!board.data) return false;
    setBusy(true);
    setNotice(null);
    try {
      const res = await action();
      // 다른 날로 옮기면 블록이 놓인 날(day)과 떠난 날(fromDay)이, 두 날을 맞바꾸면 두 날(days)이 같이 온다. 한 번에 갈아 끼운다
      const changed = "days" in res ? res.days : "fromDay" in res && res.fromDay ? [res.day, res.fromDay] : [res.day];
      const next: ScheduleBoardResponse = {
        ...board.data,
        // 맞바꾸면 일차가 달라지므로 일차 순서로 다시 줄 세운다
        days: board.data.days.map((d) => changed.find((c) => c.scheduleDayId === d.scheduleDayId) ?? d).sort((a, b) => a.dayIndex - b.dayIndex),
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

  /**
   * 끌기 시작을 화면에 반영한다. 한 박자 늦춰서 한다.
   * dragstart 안에서 바로 화면을 바꾸면(끌던 블록이 마우스를 못 받게 되는 등) 브라우저가 끌기를 그 자리에서 취소한다
   */
  function startDrag(next: Drag) {
    clearTimeout(dragTimer.current);
    dragTimer.current = setTimeout(() => {
      setEditingId(null);
      setDrag(next);
    }, 0);
  }

  function endDrag() {
    // 끌자마자 놓으면 startDrag의 예약이 아직 남아 있을 수 있다
    clearTimeout(dragTimer.current);
    setDrag(null);
    setDropAt(null);
    setOverDayId(null);
  }

  /** 블록을 다른 날의 맨 뒤로 옮긴다. 그날 안에서의 자리는 옮긴 뒤 끌어서 맞춘다 */
  async function moveToDay(item: ScheduleItemResponse, from: DaySnapshot, to: DaySnapshot) {
    // 옮겨도 시간은 그대로다. 그날 같은 시간의 일정이 이미 있으면 옮기기 전에 알려 준다
    const sameTime = item.startTime ? to.items.filter((it) => it.startTime === item.startTime) : [];
    if (sameTime.length > 0) {
      const ok = await confirm({
        title: `${to.dayIndex}일차에 ${item.startTime} 일정이 이미 있어요`,
        body: `옮기려는 곳: ${item.name}. 같은 시간 일정: ${sameTime.map((it) => it.name).join(", ")}. 옮겨도 시간은 바뀌지 않아서 같은 시간으로 남아요. 옮긴 뒤 블록을 눌러 시간을 고칠 수 있어요.`,
        confirmLabel: "그대로 옮기기",
      });
      if (!ok) return;
    }
    // 맨 뒤 = 그날 마지막 블록의 뒤. 그날이 비어 있으면 null(맨 앞)이 곧 맨 뒤다
    const last = to.items.at(-1);
    const moved = await change(() =>
      api.moveItem(roomId, item.itemId, last?.itemId ?? null, from.version, { dayId: to.scheduleDayId, dayVersion: to.version }),
    );
    if (!moved) return;
    setEditingId(null);
    setNotice({
      kind: "ok",
      text: (
        <>
          {withObject(item.name)} {to.dayIndex}일차로 옮겼어요.{" "}
          <button type="button" className="linkbtn" onClick={() => setDayIndex(to.dayIndex)}>
            {to.dayIndex}일차 보기
          </button>
        </>
      ),
    });
  }

  /** 장소를 그날의 afterItemId 블록 뒤에 새 블록으로 넣는다. 시간과 메모는 넣은 뒤 블록을 눌러 적는다 */
  function addPlace(place: PlaceCandidateItem, to: DaySnapshot, afterItemId: number | null) {
    return change(() =>
      api.addItem(roomId, to.scheduleDayId, { placeId: place.placeId, afterItemId, startTime: null, memo: null, dayVersion: to.version }),
    );
  }

  /** 두 날의 일정을 통째로 맞바꾼다 */
  async function swapDays(a: DaySnapshot, b: DaySnapshot) {
    const swapped = await change(() => api.swapDays(roomId, a.scheduleDayId, a.version, { dayId: b.scheduleDayId, dayVersion: b.version }));
    if (!swapped) return;
    setEditingId(null);
    setAddingDayId(null);
    setNotice({ kind: "ok", text: `${Math.min(a.dayIndex, b.dayIndex)}일차와 ${Math.max(a.dayIndex, b.dayIndex)}일차 일정을 서로 바꿨어요.` });
  }

  /**
   * 끌던 것을 날짜 카드에 놓을 수 있는지. 블록은 자기가 있는 날에는 놓지 못하고, 10칸이 찬 날에는 블록도 장소도 놓지 못한다.
   * 날짜 카드는 자기 자신만 아니면 어느 날에든 놓을 수 있다. 맞바꾸는 것이라 칸 수와 상관없다
   */
  function canDropOnDay(d: DaySnapshot): boolean {
    if (!editable || !drag) return false;
    if (drag.kind === "day") return drag.day.scheduleDayId !== d.scheduleDayId;
    if (drag.kind === "item" && drag.fromDayId === d.scheduleDayId) return false;
    return d.items.length < ITEMS_PER_DAY;
  }

  /** 끌던 것을 날짜 카드에 놓았다. 블록과 장소는 그날 맨 뒤로 가고, 날짜 카드는 두 날을 맞바꾼다 */
  async function dropOnDay(to: DaySnapshot) {
    if (!drag) return;
    if (drag.kind === "day") {
      // 끄는 동안 다른 멤버가 그날을 고쳤을 수 있다. 끌기 시작할 때 적어 둔 버전이 아니라 지금 보드의 것을 쓴다
      const from = days.find((d) => d.scheduleDayId === drag.day.scheduleDayId);
      if (from) await swapDays(from, to);
      return;
    }
    if (drag.kind === "place") {
      const added = await addPlace(drag.place, to, to.items.at(-1)?.itemId ?? null);
      if (added) setNotice({ kind: "ok", text: `${withObject(drag.place.name)} ${to.dayIndex}일차에 넣었어요.` });
      return;
    }
    const from = days.find((d) => d.scheduleDayId === drag.fromDayId);
    if (from) await moveToDay(drag.item, from, to);
  }

  /** 끌던 것을 d의 블록 목록에 놓을 수 있는지. 블록은 자기가 있는 날의 목록에만, 장소는 10칸이 차지 않은 날에만 놓는다. 다른 날로는 날짜 카드에 놓아 옮긴다 */
  function listAccepts(d: DaySnapshot): boolean {
    if (!editable || !drag || drag.kind === "day") return false;
    if (drag.kind === "item") return drag.fromDayId === d.scheduleDayId;
    return d.items.length < ITEMS_PER_DAY;
  }

  /** 끌던 블록이 d의 index 자리에 놓이면 실제로 자리가 바뀌는지. 같은 날의 자기 자리 앞뒤에 놓으면 그대로다 */
  function movesTo(d: DaySnapshot, index: number): boolean {
    if (!drag || drag.kind !== "item" || drag.fromDayId !== d.scheduleDayId) return true;
    const from = d.items.findIndex((it) => it.itemId === drag.item.itemId);
    return index !== from && index !== from + 1;
  }

  /** 끌던 것을 d의 index 자리(0이면 맨 앞)에 놓았다. 서버는 "어느 블록 뒤"로 받는다 */
  function dropInList(d: DaySnapshot, index: number) {
    if (!drag || drag.kind === "day") return;
    if (drag.kind === "place") {
      void addPlace(drag.place, d, index === 0 ? null : d.items[index - 1].itemId);
      return;
    }
    if (drag.fromDayId !== d.scheduleDayId || !movesTo(d, index)) return;
    // 같은 날 안에서 옮긴다. 옮길 블록을 뺀 나머지에서 앞 블록을 찾는다
    const from = d.items.findIndex((it) => it.itemId === drag.item.itemId);
    const others = d.items.filter((it) => it.itemId !== drag.item.itemId);
    const position = index > from ? index - 1 : index;
    void change(() => api.moveItem(roomId, drag.item.itemId, position === 0 ? null : others[position - 1].itemId, d.version));
  }

  /** d의 목록에서 놓일 자리 선을 그릴 곳. 놓아도 그대로인 자리에는 그리지 않는다 */
  function lineAt(d: DaySnapshot): number | null {
    return listAccepts(d) && dropAt?.dayId === d.scheduleDayId && movesTo(d, dropAt.index) ? dropAt.index : null;
  }

  /** 날짜 카드. 누르면 그날을 고르고, 블록·장소·다른 날짜 카드를 받는 자리이기도 하다 */
  function dayCard(d: DaySnapshot) {
    const droppable = canDropOnDay(d);
    // 블록이나 장소를 끌 때 10칸이 차서 받을 수 없는 날
    const full = !!drag && drag.kind !== "day" && d.items.length >= ITEMS_PER_DAY;
    // 끌고 있을 때만 붙는다. 블록이 있던 날은 그대로 두고, 꽉 찬 날만 흐리게 한다
    const dropClass = !drag ? "" : droppable ? " can-drop" : full ? " no-drop" : "";
    const dragging = drag?.kind === "day" && drag.day.scheduleDayId === d.scheduleDayId;
    // 끌던 것을 이 카드 위에 올려 둔 상태. 놓으면 무슨 일이 일어나는지 칸 수 자리에 적는다
    const over = droppable && overDayId === d.scheduleDayId;
    const countText = over ? (drag?.kind === "day" ? "이 날과 바꾸기" : "여기에 놓기") : full ? "꽉 찼어요" : `${d.items.length} / ${ITEMS_PER_DAY}칸`;
    const pick = () => {
      setNotice(null);
      setDayIndex(d.dayIndex);
      setEditingId(null);
      setAddingDayId(null);
    };
    return (
      // button은 Firefox에서 끌리지 않아서 div에 버튼 역할을 준다
      <div
        key={d.scheduleDayId}
        role="button"
        tabIndex={0}
        className={`cd${dropClass}${over ? " over" : ""}${dragging ? " dragging" : ""}`}
        aria-pressed={d.dayIndex === day?.dayIndex}
        draggable={editable && !busy && days.length > 1}
        onClick={pick}
        onKeyDown={(e) => {
          if (e.key !== "Enter" && e.key !== " ") return;
          e.preventDefault();
          pick();
        }}
        onDragStart={(e) => {
          e.dataTransfer.effectAllowed = "move";
          e.dataTransfer.setData("text/plain", `${d.dayIndex}일차`);
          startDrag({ kind: "day", day: d });
        }}
        onDragEnd={endDrag}
        onDragOver={(e) => {
          if (!droppable) return;
          e.preventDefault();
          e.dataTransfer.dropEffect = drag?.kind === "place" ? "copy" : "move";
          setOverDayId(d.scheduleDayId);
        }}
        // 옆 카드로 넘어갈 때 새 카드의 dragover가 먼저 오기도 한다. 자기 표시일 때만 지운다
        onDragLeave={() => setOverDayId((cur) => (cur === d.scheduleDayId ? null : cur))}
        onDrop={(e) => {
          e.preventDefault();
          if (droppable) void dropOnDay(d);
          endDrag();
        }}
      >
        <span className="ph">
          <b>{Number(d.date.slice(8, 10))}</b>
          <span className="t">{weekday(d.date)}요일</span>
        </span>
        <span className="tx">
          <span className="hd">
            <b>{d.dayIndex}일차</b>
            <span>{countText}</span>
          </span>
          <span className="dt">{longDate(d.date)}</span>
          <span className="k">{d.items.length > 0 ? d.items.map((it) => it.name).join(", ") : "아직 비어 있어요"}</span>
        </span>
      </div>
    );
  }

  /** 하루의 블록 목록. 블록과 장소를 받는 자리이고, 맨 아래에 넣기 버튼이 있다 */
  function dayList(d: DaySnapshot) {
    const accepts = listAccepts(d);
    const line = lineAt(d);
    const canAdd = editable && pool.length > 0 && d.items.length < ITEMS_PER_DAY;
    // 마우스가 각 블록의 가운데를 지났는지로 몇 번째 자리인지 센다
    const indexAt = (list: HTMLElement, clientY: number) =>
      Array.from(list.querySelectorAll<HTMLElement>("[data-slot]")).filter((el) => {
        const r = el.getBoundingClientRect();
        return clientY > r.top + r.height / 2;
      }).length;
    return (
      <div
        className={`daylist${accepts ? " accepting" : ""}`}
        onDragOver={(e) => {
          if (!accepts) return;
          e.preventDefault();
          e.dataTransfer.dropEffect = drag?.kind === "place" ? "copy" : "move";
          setDropAt({ dayId: d.scheduleDayId, index: indexAt(e.currentTarget, e.clientY) });
        }}
        onDragLeave={() => setDropAt((cur) => (cur?.dayId === d.scheduleDayId ? null : cur))}
        onDrop={(e) => {
          if (!accepts) return;
          e.preventDefault();
          // 선을 그릴 때 적어 둔 자리가 아니라 놓는 순간의 위치로 센다. 그 사이 표시가 지워졌어도 놓친 것이 되지 않는다
          dropInList(d, indexAt(e.currentTarget, e.clientY));
          endDrag();
        }}
      >
        {d.items.length === 0 && (
          <div className={`none${line !== null ? " drop" : ""}`}>
            <b>아직 아무것도 없는 날이에요</b>
            <span>
              {!editable
                ? "넣은 곳이 없는 날이에요"
                : pool.length > 0
                  ? "아래 '넣을 수 있는 곳'에서 끌어 넣거나 '+ 빈 칸에 넣기'를 눌러 채워요. 쉬는 날로 비워 둬도 돼요"
                  : "'다 같이 가고 싶어요'에 모인 곳을 확정하면 여기에 넣을 수 있어요. 쉬는 날로 비워 둬도 돼요"}
            </span>
          </div>
        )}
        {d.items.map((it, i) => {
          // 앞 블록과의 시간 간격. 세로 선 옆에 적어 하루의 흐름이 보이게 한다
          const gap = i > 0 ? gapLabel(d.items[i - 1].startTime, it.startTime) : null;
          return (
            <Fragment key={it.itemId}>
              {i > 0 && (
                <div className="tl-gap" aria-hidden="true">
                  <span className="tl-line" />
                  <span>{gap}</span>
                </div>
              )}
              {editable && editingId === it.itemId ? (
            <SlotEditor
              item={it}
              category={categoryOf.get(it.placeId)}
              busy={busy}
              canUp={i > 0}
              canDown={i < d.items.length - 1}
              moveTargets={days
                .filter((x) => x.scheduleDayId !== d.scheduleDayId)
                .map((x) => ({ dayId: x.scheduleDayId, dayIndex: x.dayIndex, full: x.items.length >= ITEMS_PER_DAY }))}
              onClose={() => {
                setEditingId(null);
                // 창이 사라지면 초점이 갈 곳을 잃는다. 다시 그려진 그 블록으로 돌려준다
                requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-slot][data-item="${it.itemId}"]`)?.focus());
              }}
              onUp={() => change(() => api.moveItem(roomId, it.itemId, i >= 2 ? d.items[i - 2].itemId : null, d.version))}
              onDown={() => change(() => api.moveItem(roomId, it.itemId, d.items[i + 1].itemId, d.version))}
              onMoveDay={(dayId) => {
                const to = days.find((x) => x.scheduleDayId === dayId);
                if (to) void moveToDay(it, d, to);
              }}
              onSave={(time, memo) => change(() => api.updateItem(roomId, it.itemId, time, memo, d.version))}
              onDelete={async () => {
                const ok = await confirm({
                  title: `${withObject(it.name)} ${d.dayIndex}일차에서 뺄까요?`,
                  body: "장소는 확정된 곳에 그대로 남아서 다시 넣을 수 있어요.",
                  confirmLabel: "빼기",
                });
                if (!ok) return;
                if (await change(() => api.deleteItem(roomId, it.itemId, d.version))) setEditingId(null);
              }}
            />
          ) : (
            <Slot
              item={it}
              category={categoryOf.get(it.placeId)}
              order={i + 1}
              editable={editable}
              busy={busy}
              dragging={drag?.kind === "item" && drag.item.itemId === it.itemId}
              line={line === i ? "before" : line === d.items.length && i === d.items.length - 1 ? "after" : null}
              onOpen={() => {
                setEditingId(it.itemId);
                setAddingDayId(null);
              }}
              onDragStart={() => startDrag({ kind: "item", item: it, fromDayId: d.scheduleDayId })}
              onDragEnd={endDrag}
            />
          )}
            </Fragment>
          );
        })}
        {editable && pool.length > 0 && d.items.length >= ITEMS_PER_DAY && (
          <p className="small muted" style={{ margin: "auto 0 0", paddingTop: 12 }}>
            이 날은 10칸이 꽉 찼어요
          </p>
        )}
        {canAdd && addingDayId !== d.scheduleDayId && (
          <button
            type="button"
            className="add"
            onClick={() => {
              setAddingDayId(d.scheduleDayId);
              setEditingId(null);
            }}
          >
            + 빈 칸에 넣기, {ITEMS_PER_DAY - d.items.length}칸 남음
          </button>
        )}
        {canAdd && addingDayId === d.scheduleDayId && (
          <AddItem
            day={d}
            pool={pool}
            busy={busy}
            onClose={() => {
              setAddingDayId(null);
              requestAnimationFrame(() => document.querySelector<HTMLElement>(".daylist .add")?.focus());
            }}
            onAdd={(placeId, afterItemId, time, memo) =>
              change(() => api.addItem(roomId, d.scheduleDayId, { placeId, afterItemId, startTime: time, memo, dayVersion: d.version }))
            }
          />
        )}
      </div>
    );
  }

  return (
    <>
      <div className="head">
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="head-top">
            <h1>일정</h1>
            {days.length > 1 && (
              <div className="viewsw" role="group" aria-label="보기 방식">
                <button type="button" aria-pressed={shownView === "day"} onClick={() => pickView("day")}>
                  하루씩
                </button>
                <button type="button" aria-pressed={shownView === "all"} onClick={() => pickView("all")}>
                  전체
                </button>
              </div>
            )}
          </div>
          {shownView === "all" ? (
            <p>모든 날의 일정을 한눈에 봐요{editable && ". 고치려면 날짜를 누르거나 '하루씩'으로 바꿔요"}</p>
          ) : editable ? (
            <p>
              하루 10칸까지 시간, 확정된 장소, 메모를 넣어요. 날짜별로 따로 확정하지 않고, 모두 동의해서 한 번에 확정해요. 블록을 누르면 고칠 수 있고,
              끌어서 순서와 날짜를 바꿀 수 있어요
            </p>
          ) : (
            <p>날짜별로 넣은 곳을 확인해요. 지금은 일정을 고칠 수 없어요</p>
          )}
        </div>
      </div>

      {editable && turnedDown && <TurnedDown agenda={turnedDown} />}

      <FinalReview
        status={status}
        finalAgenda={finalAgenda ?? null}
        openCustomTitles={(agendas.data?.agendas ?? [])
          .filter((a) => a.subject === "CUSTOM" && a.status === "VOTING")
          .map((a) => a.title)}
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
      {/* 아래쪽에서 블록을 고치거나 장소를 넣어도 결과 안내가 보이게 화면 위쪽에 붙여 둔다.
          자리는 늘 만들어 두고 글만 바꾼다. 안내가 생길 때 자리째 새로 만들면 스크린 리더가 못 읽는 경우가 있다 */}
      <div className={`notice-stick${drag ? " dragging" : ""}`} aria-live={notice?.kind === "err" ? "assertive" : "polite"}>
        {notice && (
          <div className={`notice ${notice.kind}`}>
            <span>{notice.text}</span>
            <button type="button" className="notice-close" onClick={() => setNotice(null)}>
              닫기
            </button>
          </div>
        )}
      </div>

      {days.length > 0 && day && shownView === "day" && (
        <>
          {/* 하루짜리 여행은 고를 날이 없다 */}
          {days.length > 1 && (
            <nav className={days.length >= 5 ? "dsel many" : "dsel"} aria-label="날짜 고르기">
              {days.map((d) => dayCard(d))}
            </nav>
          )}

          <section className="card daybox" aria-labelledby="day-h">
            <header className="daybar">
              <div className="daybar-main">
                <span className="daypill">{day.dayIndex}일차</span>
                <h2 id="day-h">{longDate(day.date)}</h2>
                <span className="daymeter">
                  <span className="daymeter-track" aria-hidden="true">
                    <span style={{ width: `${(day.items.length / ITEMS_PER_DAY) * 100}%` }} />
                  </span>
                  <span className="small muted">
                    {day.items.length} / {ITEMS_PER_DAY}칸
                  </span>
                </span>
              </div>
            {editable && days.length > 1 && (
              // 고르는 순간 맞바꾼다. value를 ""로 고정해서 상자에는 늘 같은 글이 보인다
              <select
                className="swap"
                aria-label={`${day.dayIndex}일차 일정을 다른 날과 통째로 바꾸기`}
                disabled={busy}
                value=""
                onChange={(e) => {
                  const other = days.find((d) => d.scheduleDayId === Number(e.target.value));
                  if (other) void swapDays(day, other);
                }}
              >
                <option value="">다른 날과 통째로 바꾸기</option>
                {days
                  .filter((d) => d.scheduleDayId !== day.scheduleDayId)
                  .map((d) => (
                    <option key={d.scheduleDayId} value={d.scheduleDayId}>
                      {d.dayIndex}일차와 바꾸기
                    </option>
                  ))}
              </select>
            )}
            </header>
            {dayList(day)}
          </section>
        </>
      )}

      {days.length > 0 && day && shownView === "all" && (
        // 모든 날을 칸으로 나란히 놓고 보기만 한다. 날이 많으면 옆으로 넘긴다. 고치는 일은 '하루씩'에서 한다
        <div className="allboard" role="list" aria-label="전체 일정">
          {days.map((d) => (
            <section key={d.scheduleDayId} className="daycol" role="listitem" aria-label={`${d.dayIndex}일차, ${longDate(d.date)}`}>
              <button
                type="button"
                className="cd sm"
                aria-label={`${d.dayIndex}일차, ${longDate(d.date)}. 눌러서 이 날만 보기`}
                onClick={() => {
                  setDayIndex(d.dayIndex);
                  pickView("day");
                }}
              >
                <span className="ph">
                  <b>{Number(d.date.slice(8, 10))}</b>
                  <span className="t">{weekday(d.date)}요일</span>
                </span>
                <span className="tx">
                  <span className="hd">
                    <b>{d.dayIndex}일차</b>
                    <span className="meta-go">
                      {d.items.length}곳
                      <span className="go" aria-hidden="true">
                        ›
                      </span>
                    </span>
                  </span>
                  <span className="dt">{longDate(d.date)}</span>
                </span>
              </button>
              <div className="daylist">
                {d.items.length === 0 && <p className="rest">아직 비어 있어요</p>}
                {d.items.map((it) => (
                  <Slot
                    key={it.itemId}
                    item={it}
                    category={categoryOf.get(it.placeId)}
                    editable={false}
                    busy={busy}
                    order={0}
                    compact
                    dragging={false}
                    line={null}
                    onOpen={() => {}}
                    onDragStart={() => {}}
                    onDragEnd={() => {}}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {days.length > 0 && day && shownView === "day" && editable && pool.length > 0 && (
        <section className="card poolbox" aria-labelledby="pool-h">
          <div className="pool-head">
            <div style={{ minWidth: 0 }}>
              <h2 id="pool-h">넣을 수 있는 곳</h2>
              <p className="small muted" style={{ margin: "2px 0 0" }}>
                {day.items.length >= ITEMS_PER_DAY
                  ? `${day.dayIndex}일차는 10칸이 꽉 찼어요. 다른 날을 고르거나 날짜 카드로 끌어서 넣을 수 있어요`
                  : `확정된 곳이에요. 누르면 ${day.dayIndex}일차 맨 뒤에 들어가고, 끌어서 원하는 자리나 날짜에 놓을 수 있어요`}
              </p>
            </div>
            <Link href={`/rooms/${roomId}/places`} className="btn btn-line btn-sm">
              가고 싶은 곳에서 더 확정하기
            </Link>
          </div>
          {/* 종류별 칸으로 나눈다. 곳이 없는 종류의 칸은 그리지 않는다 */}
          <div className="poolboard">
            {POOL_GROUPS.map((group) => {
              const list = pool.filter((p) => (p.category?.value ?? "ETC") === group);
              if (list.length === 0) return null;
              const label = group === "ETC" ? "기타" : CATEGORY_TAG[group];
              return (
                <section key={group} className="poolcol" aria-label={`${label} ${list.length}곳`}>
                  <h3 className="poolcol-h">
                    <CategoryIcon category={group === "ETC" ? null : group} />
                    <b>{label}</b>
                    <span className="n">{list.length}</span>
                  </h3>
                  <div className="poolcol-list">
                    {list.map((p) => {
                    const usedOn = days.filter((d) => d.items.some((it) => it.placeId === p.placeId)).map((d) => d.dayIndex);
                    const full = day.items.length >= ITEMS_PER_DAY;
                    // 여러 날에 넣은 곳은 줄여 적는다. 다 적으면 칩이 여러 줄로 꺾인다
                    const used =
                      usedOn.length === 0
                        ? "아직 안 넣었어요"
                        : usedOn.length > 3
                          ? `${usedOn.slice(0, 2).join(", ")}일차 외 ${usedOn.length - 2}일에 있어요`
                          : `${usedOn.join(", ")}일차에 있어요`;
                    const add = () => {
                      if (busy) return;
                      // 지금 보는 날이 꽉 차도 칩은 다른 날로 끌 수 있어서 흐리게 막지 않고, 눌렀을 때 이유를 알려 준다
                      if (full) {
                        setNotice({ kind: "warn", text: `${day.dayIndex}일차는 10칸이 꽉 찼어요. 다른 날을 고르거나 날짜 카드로 끌어 주세요.` });
                        return;
                      }
                      void addPlace(p, day, day.items.at(-1)?.itemId ?? null);
                    };
                    return (
                      // button은 Firefox에서 끌리지 않아서 div에 버튼 역할을 준다
                      <div
                        key={p.placeId}
                        className={`pool${drag?.kind === "place" && drag.place.placeId === p.placeId ? " dragging" : ""}`}
                        role="button"
                        tabIndex={0}
                        aria-disabled={busy}
                        aria-label={`${p.name}, ${used}. ${full ? `${day.dayIndex}일차는 꽉 찼어요` : `누르면 ${day.dayIndex}일차 맨 뒤에 넣어요`}`}
                        title={p.name}
                        draggable={!busy}
                        onClick={add}
                        onKeyDown={(e) => {
                          if (e.key !== "Enter" && e.key !== " ") return;
                          e.preventDefault();
                          add();
                        }}
                        onDragStart={(e) => {
                          e.dataTransfer.effectAllowed = "copy";
                          e.dataTransfer.setData("text/plain", p.name);
                          startDrag({ kind: "place", place: p });
                        }}
                        onDragEnd={endDrag}
                      >
                        <span className="pool-tx">
                          <b>{p.name}</b>
                          <span className={usedOn.length === 0 ? "small fresh" : "small muted"}>{used}</span>
                        </span>
                        {!full && (
                          <span className="pool-plus" aria-hidden="true">
                            +
                          </span>
                        )}
                      </div>
                    );
                    })}
                  </div>
                </section>
              );
            })}
          </div>
        </section>
      )}

      {dialog}

      {shownView === "day" && editable && places.data && pool.length === 0 && (
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
  openCustomTitles,
  dayCounts,
  solo,
  onOpened,
}: {
  status: string;
  finalAgenda: import("@/lib/types").AgendaListItem | null;
  /** 아직 투표 중인 자유 안건 제목. 최종 투표를 열면 이 안건들은 못 정한 채로 끝난다 */
  openCustomTitles: string[];
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
    <section className="card finalbar">
      <div className="stack" style={{ gap: 2 }}>
        <b>일정을 다 짰나요?</b>
        <span className="small muted">최종 확정 투표를 열면 일정이 잠기고, 모두 동의하면 확정돼요. 한 명이라도 반대하면 다시 고칠 수 있어요</span>
      </div>
      <button
        type="button"
        className="btn btn-line"
        disabled={busy}
        onClick={async () => {
          // 투표를 연 사람은 서버가 동의로 넣는다. 그래서 혼자인 방은 열자마자 확정된다
          const notes = solo ? ["지금 방에 나 혼자라서 열자마자 일정이 확정돼요. 확정하면 되돌릴 수 없어요."] : [];
          if (openCustomTitles.length > 0) {
            notes.push(
              `아직 정하지 못한 안건이 ${openCustomTitles.length}개 있어요(${openCustomTitles.join(", ")}). 최종 투표를 열면 이 안건들은 못 정한 채로 끝나요.`,
            );
          }
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

/** 일정 블록 한 줄. 끌어서 옮기고, 누르면 고치는 상태로 열린다 */
function Slot({
  item,
  category,
  editable,
  busy,
  order,
  compact = false,
  dragging,
  line,
  onOpen,
  onDragStart,
  onDragEnd,
}: {
  item: ScheduleItemResponse;
  category: PlaceCategory | null | undefined;
  /** 그날에서 몇 번째 블록인지 (1부터) */
  order: number;
  /** 전체 보기의 좁은 칸에 넣을 때. 순번과 카드 없이 한 줄로 그린다 */
  compact?: boolean;
  editable: boolean;
  busy: boolean;
  dragging: boolean;
  /** 끌던 것이 이 블록의 앞이나 뒤에 놓일 때 그 자리에 선을 그린다 */
  line: "before" | "after" | null;
  onOpen: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
}) {
  const body = compact ? (
    <>
      <span className={item.startTime ? "tm" : "tm none"}>{item.startTime ?? "미정"}</span>
      <span className="pl">
        <CategoryIcon category={category} />
        <span>
          <b>{item.name}</b>
          <span className="memo">{item.memo || item.address}</span>
        </span>
      </span>
    </>
  ) : (
    <>
      <span className={item.startTime ? "tm" : "tm none"}>{item.startTime ?? "미정"}</span>
      <span className="tl-node" aria-hidden="true">
        {order}
      </span>
      <span className="tl-card">
        <CategoryIcon category={category} />
        <span className="tl-text">
          <b>{item.name}</b>
          {item.memo && <span className="memo">{item.memo}</span>}
          <span className="addr">{item.address}</span>
        </span>
        {category && <span className="tag n">{CATEGORY_TAG[category]}</span>}
        {editable && (
          // 끌 수 있다는 표시. 커서만으로는 처음 보는 사람이 알기 어렵다
          <span className="grip" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <circle cx="5.5" cy="3.5" r="1.2" />
              <circle cx="10.5" cy="3.5" r="1.2" />
              <circle cx="5.5" cy="8" r="1.2" />
              <circle cx="10.5" cy="8" r="1.2" />
              <circle cx="5.5" cy="12.5" r="1.2" />
              <circle cx="10.5" cy="12.5" r="1.2" />
            </svg>
          </span>
        )}
      </span>
    </>
  );
  if (!editable) {
    return (
      <div className={compact ? "slot" : "slot tl"} data-slot>
        {body}
      </div>
    );
  }
  return (
    // button은 Firefox에서 끌리지 않아서 div에 버튼 역할을 준다
    <div
      className={`slot tl can-edit${dragging ? " dragging" : ""}${line ? ` drop-${line}` : ""}`}
      data-slot
      data-item={item.itemId}
      role="button"
      tabIndex={0}
      aria-label={`${item.name}, ${item.startTime ?? "시간 미정"}. 눌러서 고치기`}
      draggable={!busy}
      onClick={onOpen}
      onKeyDown={(e) => {
        if (e.key !== "Enter" && e.key !== " ") return;
        e.preventDefault();
        onOpen();
      }}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move";
        // Firefox는 데이터를 실어야 끌기가 시작된다
        e.dataTransfer.setData("text/plain", item.name);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
    >
      {body}
    </div>
  );
}

/** 눌러서 연 블록. 시간과 메모를 고치고, 끌 수 없는 화면(터치, 키보드)에서도 옮기고 뺄 수 있게 버튼을 둔다 */
function SlotEditor({
  item,
  category,
  busy,
  canUp,
  canDown,
  moveTargets,
  onClose,
  onUp,
  onDown,
  onMoveDay,
  onSave,
  onDelete,
}: {
  item: ScheduleItemResponse;
  category: PlaceCategory | null | undefined;
  busy: boolean;
  canUp: boolean;
  canDown: boolean;
  /** 이 블록을 옮길 수 있는 다른 날. full이면 10칸이 차서 고를 수 없다 */
  moveTargets: { dayId: number; dayIndex: number; full: boolean }[];
  onClose: () => void;
  onUp: () => void;
  onDown: () => void;
  onMoveDay: (dayId: number) => void;
  onSave: (time: string | null, memo: string | null) => Promise<boolean>;
  onDelete: () => void;
}) {
  const [time, setTime] = useState(item.startTime ?? "");
  const [memo, setMemo] = useState(item.memo ?? "");
  const timeRef = useRef<HTMLInputElement>(null);
  const upRef = useRef<HTMLButtonElement>(null);
  const downRef = useRef<HTMLButtonElement>(null);

  // 블록을 누르면 그 블록이 이 폼으로 바뀌면서 초점이 사라진다. 열리자마자 첫 칸으로 옮겨 키보드로 이어서 쓸 수 있게 한다
  useEffect(() => {
    timeRef.current?.focus();
  }, []);

  // 위로·아래로를 누르다 맨 끝에 닿으면 그 버튼이 꺼지면서 초점이 묶인다. 반대쪽 버튼으로 넘긴다
  useEffect(() => {
    if (!canUp && document.activeElement === upRef.current) downRef.current?.focus();
    if (!canDown && document.activeElement === downRef.current) upRef.current?.focus();
  }, [canUp, canDown]);

  return (
    <form
      className="slot open"
      data-slot
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
      onSubmit={async (e) => {
        e.preventDefault();
        // 실패하면 고치던 창을 그대로 둬서 같은 값으로 다시 저장할 수 있게 한다
        if (await onSave(time || null, memo.trim() || null)) onClose();
      }}
    >
      <span className="pl">
        <CategoryIcon category={category} />
        <span>
          <b>{item.name}</b>
          <span className="memo">{item.address}</span>
        </span>
      </span>
      <div className="row" style={{ alignItems: "flex-end" }}>
        <label className="f tm-field">
          <span className="lb small lb-row">
            <span>시간 (없어도 돼요)</span>
            {/* 휴대폰의 시간 고르기에는 비우는 방법이 없는 경우가 있다 */}
            {time && (
              <button type="button" className="linkbtn" onClick={() => setTime("")}>
                지우기
              </button>
            )}
          </span>
          <input ref={timeRef} className="in" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
        <label className="f grow" style={{ minWidth: 200 }}>
          <span className="lb small">메모 (없어도 돼요)</span>
          <input className="in" maxLength={200} value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 점심, 예약 12시" />
        </label>
      </div>
      <div className="row">
        <button type="submit" className="btn btn-accent btn-sm" disabled={busy}>
          저장
        </button>
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          그만두기
        </button>
        <span className="mv">
          <button ref={upRef} type="button" className="btn btn-ghost" disabled={busy || !canUp} onClick={onUp} aria-label={`${item.name} 위로`}>
            위로
          </button>
          <button ref={downRef} type="button" className="btn btn-ghost" disabled={busy || !canDown} onClick={onDown} aria-label={`${item.name} 아래로`}>
            아래로
          </button>
          {moveTargets.length > 0 && (
            // 고르는 순간 옮긴다. value를 ""로 고정해서 상자에는 늘 "다른 날로"가 보인다
            <select
              className="to-day"
              aria-label={`${item.name} 다른 날로 옮기기`}
              disabled={busy}
              value=""
              onChange={(e) => {
                if (e.target.value) onMoveDay(Number(e.target.value));
              }}
            >
              <option value="">다른 날로</option>
              {moveTargets.map((t) => (
                <option key={t.dayId} value={t.dayId} disabled={t.full}>
                  {t.dayIndex}일차{t.full ? " (꽉 찼어요)" : ""}
                </option>
              ))}
            </select>
          )}
          <button type="button" className="btn btn-danger btn-sm" disabled={busy} onClick={onDelete} aria-label={`${item.name} 빼기`}>
            빼기
          </button>
        </span>
      </div>
    </form>
  );
}

/** 장소, 자리, 시간, 메모를 정해서 넣는 폼. '+ 빈 칸에 넣기'를 누르면 열린다 */
function AddItem({
  day,
  pool,
  busy,
  onClose,
  onAdd,
}: {
  day: DaySnapshot;
  pool: PlaceCandidateItem[];
  busy: boolean;
  onClose: () => void;
  onAdd: (placeId: number, afterItemId: number | null, time: string | null, memo: string | null) => Promise<boolean>;
}) {
  const [placeId, setPlaceId] = useState("");
  // "end"면 맨 뒤, "start"면 맨 앞, 숫자면 그 블록 뒤
  const [after, setAfter] = useState("end");
  const [time, setTime] = useState("");
  const [memo, setMemo] = useState("");
  const placeRef = useRef<HTMLSelectElement>(null);

  useEffect(() => {
    placeRef.current?.focus();
  }, []);

  return (
    <form
      className="addform"
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
      onSubmit={async (e) => {
        e.preventDefault();
        if (!placeId) return;
        const last = day.items[day.items.length - 1];
        // 고른 자리의 블록을 그 사이 다른 멤버가 뺐을 수 있다. 그러면 맨 뒤로 넣는다
        const picked = after === "start" || after === "end" || day.items.some((it) => it.itemId === Number(after)) ? after : "end";
        const afterItemId = picked === "start" ? null : picked === "end" ? (last?.itemId ?? null) : Number(picked);
        // 실패하면 고른 장소와 입력한 시간·메모를 남겨 둔다
        if (await onAdd(Number(placeId), afterItemId, time || null, memo.trim() || null)) onClose();
      }}
    >
      <b>빈 칸에 넣기, {ITEMS_PER_DAY - day.items.length}칸 남음</b>
      <div className="addgrid">
        <label className={`f${day.items.length === 0 ? " span2" : ""}`}>
          <span className="lb small">확정된 곳</span>
          <select ref={placeRef} className="in" value={placeId} onChange={(e) => setPlaceId(e.target.value)} required>
            <option value="">고르기</option>
            {pool.map((p) => (
              <option key={p.placeId} value={p.placeId}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        {/* 빈 날에는 맨 앞과 맨 뒤가 같은 자리라 고를 것이 없다 */}
        {day.items.length > 0 && (
          <label className="f">
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
        )}
        <label className="f">
          <span className="lb small lb-row">
            <span>시간 (없어도 돼요)</span>
            {time && (
              <button type="button" className="linkbtn" onClick={() => setTime("")}>
                지우기
              </button>
            )}
          </span>
          <input className="in" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
        <label className="f">
          <span className="lb small">메모 (없어도 돼요)</span>
          <input className="in" maxLength={200} value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 점심, 예약 12시" />
        </label>
      </div>
      <div className="row">
        <button type="submit" className="btn btn-accent btn-sm" disabled={busy || !placeId}>
          넣기
        </button>
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          그만두기
        </button>
      </div>
    </form>
  );
}
