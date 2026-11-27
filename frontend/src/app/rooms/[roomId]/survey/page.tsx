"use client";

import Link from "next/link";
import { useState } from "react";
import { useRoom } from "@/components/room";
import { useConfirm } from "@/components/ConfirmDialog";
import { Avatar, Loading, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { won } from "@/lib/format";
import { requestRefresh, useApi } from "@/lib/hooks";
import type { ActivityCode, Pace, StayType, SurveyOptionsResponse } from "@/lib/types";

export default function SurveyPage() {
  const { roomId, room, myMember, reloadRoom } = useRoom();
  const options = useApi("survey-options", api.surveyOptions);
  const submitted = Boolean(myMember?.surveySubmitted);

  return (
    <>
      <div className="head">
        <div>
          <h1>설문</h1>
          <p>방에 들어오면 설문부터 해요. 모두 같은 답은 바로 정해지고, 갈린 것은 같이 정하기에서 맞춰요</p>
        </div>
        {room.surveyClosedAt && (
          <Link className="btn btn-accent" href={`/rooms/${roomId}/vote`}>
            같이 정하기로 가기
          </Link>
        )}
      </div>

      <section aria-label="제출 현황" className="row" style={{ gap: 10 }}>
        {room.members.map((m) => (
          <span key={m.memberId} className="row" style={{ gap: 8, height: 40, padding: "0 14px 0 6px", borderRadius: 999, background: "var(--surface)", boxShadow: "inset 0 0 0 1px var(--line)", fontSize: 14 }}>
            <Avatar name={m.name} />
            {m.name}, {m.surveySubmitted ? "냈어요" : "아직"}
          </span>
        ))}
      </section>

      {options.loading && <Loading />}
      {options.error && <Notice kind="err">{options.error.message}</Notice>}
      {options.data &&
        (submitted ? (
          <MySurvey />
        ) : room.status === "FINALIZED" || room.status === "EXPIRED" ? (
          <Notice>이 방은 설문을 더 받지 않아요.</Notice>
        ) : (
          <SurveyForm
            options={options.data}
            onDone={async () => {
              await reloadRoom();
              requestRefresh();
            }}
          />
        ))}
    </>
  );
}

function MySurvey() {
  const { roomId } = useRoom();
  const mine = useApi(`survey-me:${roomId}`, () => api.mySurvey(roomId));
  if (mine.loading) return <Loading />;
  if (!mine.data) {
    return <Notice kind="ok">설문을 냈어요. 모두 내면 갈린 것만 같이 정하기에 올라와요.</Notice>;
  }
  const s = mine.data;
  return (
    <section className="card" aria-label="내 답변" style={{ gap: 20 }}>
      <Notice kind="ok">
        <b>설문을 냈어요.</b> 낸 답은 고칠 수 없어요
      </Notice>
      <dl style={{ margin: 0, display: "grid", gridTemplateColumns: "max-content 1fr", gap: "10px 20px" }}>
        {s.budget !== null && (
          <>
            <dt className="muted">1인 예산</dt>
            <dd style={{ margin: 0 }}>{won(s.budget)}</dd>
          </>
        )}
        {s.stayType && (
          <>
            <dt className="muted">숙소</dt>
            <dd style={{ margin: 0 }}>{s.stayType.label}</dd>
          </>
        )}
        {s.pace && (
          <>
            <dt className="muted">하루 속도</dt>
            <dd style={{ margin: 0 }}>{s.pace.label}</dd>
          </>
        )}
        <dt className="muted">하고 싶은 것</dt>
        <dd style={{ margin: 0 }}>{s.actPrefs.map((a) => a.label).join(", ")}</dd>
        <dt className="muted">피하고 싶은 것</dt>
        <dd style={{ margin: 0 }}>{s.avoidNote}</dd>
      </dl>
    </section>
  );
}

function SurveyForm({ options, onDone }: { options: SurveyOptionsResponse; onDone: () => Promise<void> }) {
  const { roomId, room } = useRoom();
  // 조율 중(SURVEYING)이면 설문 마감 뒤에도 예산, 숙소, 속도를 묻는다. 이미 투표 중인 항목은 다음 회차부터 선지에 들어간다.
  // 계획 단계부터는 이미 정해진 것이라 빼고 낸다
  const full = room.status === "SURVEYING";
  const late = full && Boolean(room.surveyClosedAt);
  const { limits } = options;
  const [budget, setBudget] = useState("");
  const [stayType, setStayType] = useState<StayType | null>(null);
  const [pace, setPace] = useState<Pace | null>(null);
  const [acts, setActs] = useState<ActivityCode[]>([]);
  const [avoid, setAvoid] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [closedNow, setClosedNow] = useState(false);
  const { confirm, dialog } = useConfirm();

  function toggleAct(code: ActivityCode) {
    setActs((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : prev.length >= limits.actPrefsMax ? prev : [...prev, code],
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const budgetWon = budget ? Math.round(Number(budget) * 10_000) : null;
    if (full) {
      if (!budgetWon || budgetWon < limits.budgetMin || budgetWon > limits.budgetMax) return setError("1인 예산을 만원 단위로 넣어 주세요.");
      if (!stayType) return setError("숙소 종류를 골라 주세요.");
      if (!pace) return setError("하루 속도를 골라 주세요.");
    }
    if (acts.length < limits.actPrefsMin) return setError(`하고 싶은 것을 ${limits.actPrefsMin}개 이상 골라 주세요.`);
    if (!avoid.trim()) return setError("피하고 싶은 것을 적어 주세요. 없으면 '없어요'라고 적어도 돼요.");
    const remaining = room.members.filter((member) => !member.surveySubmitted).length;
    if (room.members.length === 1 || (room.members.length >= 2 && remaining === 1)) {
      const message =
        room.members.length === 1
          ? "제출하면 설문이 마감되고 같이 정하기 없이 바로 가고 싶은 곳을 추가할 수 있어요"
          : "제출하면 설문이 마감되고 투표가 시작돼요";
      const confirmed = await confirm({ title: "설문 제출", body: message, confirmLabel: "확인", cancelLabel: "취소" });
      if (!confirmed) return;
    }
    await submit(budgetWon);
  }

  async function submit(budgetWon: number | null) {
    setBusy(true);
    setError(null);
    try {
      const res = await api.submitSurvey(roomId, {
        budget: full ? budgetWon : null,
        stayType: full ? stayType : null,
        pace: full ? pace : null,
        actPrefs: acts,
        avoidNote: avoid.trim(),
      });
      setClosedNow(res.surveyClosed);
      await onDone();
    } catch (err) {
      setError(err instanceof ApiError && err.code === "INVALID_INPUT" ? "답을 다시 확인해 주세요." : errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <>
    {dialog}
    <form className="card" aria-label="내 답변" style={{ gap: 30 }} onSubmit={onSubmit}>
      {closedNow && <Notice kind="ok">모두 냈어요. 갈린 것은 같이 정하기에 올라왔어요.</Notice>}
      {late && <Notice>설문은 이미 마감됐어요. 예산, 숙소, 하루 속도는 지금 투표에는 들어가지 않고, 다시 투표할 때부터 선지에 들어가요.</Notice>}
      {!full && <Notice>예산, 숙소, 하루 속도는 이미 정하고 있어서 하고 싶은 것과 피하고 싶은 것만 받아요.</Notice>}
      {full && (
        <>
          <fieldset style={{ border: 0, margin: 0, padding: 0 }} className="stack">
            <legend className="stack" style={{ gap: 2, marginBottom: 12 }}>
              <b style={{ fontSize: 18 }}>1인 예산은 어느 정도가 좋아요?</b>
              <span className="small muted">숙소와 항공권은 빼고, 여행 전체에 쓰는 돈이에요</span>
            </legend>
            <div className="row" style={{ flexWrap: "nowrap", maxWidth: 260 }}>
              <label htmlFor="budget" className="sr-only">
                1인 예산 (만원)
              </label>
              <input id="budget" className="in" inputMode="numeric" placeholder="예: 140" value={budget} onChange={(e) => setBudget(e.target.value.replace(/[^0-9]/g, ""))} />
              <span>만원</span>
            </div>
          </fieldset>
          <fieldset style={{ border: 0, margin: 0, padding: 0 }}>
            <legend style={{ marginBottom: 12 }}>
              <b style={{ fontSize: 18 }}>숙소는 어떤 곳이 좋아요?</b>
            </legend>
            <div className="tiles" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
              {options.stayTypes.map((o) => (
                <button key={o.value} type="button" className="tile" aria-pressed={stayType === o.value} onClick={() => setStayType(o.value as StayType)}>
                  {o.label}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset style={{ border: 0, margin: 0, padding: 0 }}>
            <legend style={{ marginBottom: 12 }}>
              <b style={{ fontSize: 18 }}>하루 일정은 어떤 속도가 좋아요?</b>
            </legend>
            <div className="tiles" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
              {options.paces.map((o) => (
                <button key={o.value} type="button" className="tile" aria-pressed={pace === o.value} onClick={() => setPace(o.value as Pace)}>
                  {o.label}
                </button>
              ))}
            </div>
          </fieldset>
        </>
      )}
      <fieldset style={{ border: 0, margin: 0, padding: 0 }}>
        <legend className="stack" style={{ gap: 2, marginBottom: 12 }}>
          <b style={{ fontSize: 18 }}>이번 여행에서 하고 싶은 것</b>
          <span className="small muted">{limits.actPrefsMax}개까지 골라요</span>
        </legend>
        <div className="chips">
          {options.actPrefs.map((o) => (
            <button key={o.value} type="button" className="chip" aria-pressed={acts.includes(o.value as ActivityCode)} onClick={() => toggleAct(o.value as ActivityCode)}>
              {o.label}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="f">
        <label htmlFor="avoid">
          <b style={{ fontSize: 18 }}>이건 좀 피하고 싶어요</b>
        </label>
        <span className="help">자유롭게 적어요. 가고 싶은 곳을 고를 때와 최종 확정 전에 친구들이 봐요</span>
        <textarea id="avoid" className="in" rows={2} maxLength={limits.avoidNoteMaxLength} value={avoid} onChange={(e) => setAvoid(e.target.value)} placeholder="예: 날 생선은 못 먹어요" />
        <span className="help" style={{ alignSelf: "flex-end" }}>
          {avoid.length} / {limits.avoidNoteMaxLength}
        </span>
      </div>
      {error && <Notice kind="err">{error}</Notice>}
      <div>
        <button type="submit" className="btn btn-accent btn-lg" disabled={busy}>
          {busy ? "내는 중" : "설문 내기"}
        </button>
        <p className="small muted" style={{ marginTop: 8 }}>
          낸 뒤에는 고칠 수 없어요
        </p>
      </div>
    </form>
    </>
  );
}
