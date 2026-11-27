"use client";

import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { addDays, todayKst, tripDays, tripLength } from "@/lib/format";
import { useLoginRedirect } from "@/lib/hooks";

// 브라우저 기본 날짜 칸은 브라우저 언어에 따라 mm/dd/yyyy로 보인다.
// 달력은 브라우저 것을 그대로 쓰고, 칸에 보이는 글자만 YYYY/MM/DD로 그린다.
function DateField({
  id,
  value,
  min,
  max,
  onChange,
}: {
  id: string;
  value: string;
  min: string;
  max: string;
  onChange: (value: string) => void;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);
  return (
    <div style={{ position: "relative" }}>
      <div
        className="in"
        aria-hidden="true"
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          outline: focused ? "2px solid var(--accent)" : undefined,
          outlineOffset: -2,
        }}
      >
        <span>{value ? value.replaceAll("-", "/") : "YYYY/MM/DD"}</span>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 11h18" />
        </svg>
      </div>
      {/* 진짜 입력칸은 보이지 않게 위에 겹쳐 두고, 누르면 달력을 연다 */}
      <input
        ref={ref}
        id={id}
        type="date"
        min={min}
        max={max}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onClick={() => {
          try {
            ref.current?.showPicker();
          } catch {
            // showPicker를 못 쓰는 브라우저는 기본 동작에 맡긴다
          }
        }}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        required
        style={{ position: "absolute", inset: 0, width: "100%", height: "100%", opacity: 0, cursor: "pointer" }}
      />
    </div>
  );
}

export default function NewRoomPage() {
  const router = useRouter();
  const toLogin = useLoginRedirect();
  const today = todayKst();
  // 백엔드 규칙: 출발일 0시가 지금부터 24시간 넘게 남아야 하고, 1년 안이어야 하고, 31일까지
  const minStart = addDays(today, 2);
  const maxStart = addDays(today, 364);
  const [name, setName] = useState("");
  const [destination, setDestination] = useState("");
  const [startDate, setStartDate] = useState(minStart);
  const [endDate, setEndDate] = useState(addDays(minStart, 2));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const days = useMemo(() => (endDate >= startDate ? tripDays(startDate, endDate) : 0), [startDate, endDate]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (days < 1 || days > 31) {
      setError("여행 기간은 1일부터 31일까지예요. 오는 날을 다시 골라 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const room = await api.createRoom({ name: name.trim(), destination: destination.trim(), startDate, endDate });
      router.replace(`/rooms/${room.roomId}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        toLogin();
        return;
      }
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <AuthShell
      photo="castle"
      big={
        <>
          친구들 취향이
          <br />
          착착 맞는 여행
        </>
      }
      sub="설문으로 서로 바라는 걸 먼저 모으고, 갈리는 건 같이 정해요"
    >
      <div>
        <h1>새 여행방 만들기</h1>
        <p className="muted" style={{ marginTop: 6 }}>
          방을 만들면 초대 링크가 생겨요. 링크로 들어온 친구는 설문부터 시작해요
        </p>
      </div>
      <form onSubmit={onSubmit} className="stack" style={{ gap: 16 }}>
        <div className="f">
          <label htmlFor="name">여행 이름</label>
          <input id="name" className="in" maxLength={50} placeholder="예: 오사카 겨울 여행" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="f">
          <label htmlFor="dest">여행지</label>
          <input id="dest" className="in" maxLength={50} placeholder="예: 일본 오사카" value={destination} onChange={(e) => setDestination(e.target.value)} required />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div className="f">
            <label htmlFor="start">가는 날</label>
            <DateField
              id="start"
              min={minStart}
              max={maxStart}
              value={startDate}
              onChange={(v) => {
                setStartDate(v);
                if (endDate < v) setEndDate(v);
              }}
            />
          </div>
          <div className="f">
            <label htmlFor="end">오는 날</label>
            <DateField id="end" min={startDate} max={addDays(startDate, 30)} value={endDate} onChange={setEndDate} />
          </div>
        </div>
        <p className="small muted">
          {days > 0 ? `${tripLength(startDate, endDate)} 여행이에요. ` : ""}출발까지 24시간 넘게 남았고, 1년 안에 떠나는 여행만 만들 수 있어요
        </p>
        <div className="stack" style={{ gap: 8 }}>
          <div className="notice">
            <b>설문은 3일 안에</b>
            <br />
            <span className="small muted">방을 만든 때부터 3일 동안 의견을 모아요</span>
          </div>
          {days > 0 && (
            <div className="notice">
              <b>가고 싶은 곳 후보는 {days * 10}곳까지</b>
              <br />
              <span className="small muted">여행 하루당 10곳씩이에요. 일정도 하루 10칸까지예요</span>
            </div>
          )}
        </div>
        {error && <Notice kind="err">{error}</Notice>}
        <button type="submit" className="btn btn-accent btn-lg" disabled={busy}>
          {busy ? "만드는 중" : "방 만들기"}
        </button>
      </form>
    </AuthShell>
  );
}
