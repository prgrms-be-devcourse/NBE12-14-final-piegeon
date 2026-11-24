"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { addDays, todayKst, tripDays, tripLength } from "@/lib/format";
import { useLoginRedirect } from "@/lib/hooks";

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
            <input
              id="start"
              className="in"
              type="date"
              min={minStart}
              max={maxStart}
              value={startDate}
              onChange={(e) => {
                setStartDate(e.target.value);
                if (endDate < e.target.value) setEndDate(e.target.value);
              }}
              required
            />
          </div>
          <div className="f">
            <label htmlFor="end">오는 날</label>
            <input id="end" className="in" type="date" min={startDate} max={addDays(startDate, 30)} value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
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
