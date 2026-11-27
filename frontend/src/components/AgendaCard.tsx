"use client";

import { useEffect, useState } from "react";
import { useConfirm } from "@/components/ConfirmDialog";
import { useRoom } from "@/components/room";
import { Avatar, Faces, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { addDays, dateTimeLabel, feedTime, nowKst, remaining } from "@/lib/format";
import { requestRefresh, topics, useApi } from "@/lib/hooks";
import type { AgendaDetailResponse, AgendaListItem, CommentResponse } from "@/lib/types";

const BUDGET_MAX = 99_999_999;
// 서버(AgendaItem.VOTING_DAYS)와 같다. 다음 회차는 지금부터 7일 뒤에 마감하고, 출발일 0시보다 늦으면 출발일 0시로 줄인다
const NEXT_ROUND_VOTING_DAYS = 7;

/** 다음 회차를 지금 열면 언제 마감되는지. 서버가 정하는 값을 미리 보여 주려고 같은 규칙으로 계산한다 */
function nextRoundDeadline(startDate: string): string {
  const now = nowKst();
  const candidate = `${addDays(now.slice(0, 10), NEXT_ROUND_VOTING_DAYS)}T${now.slice(11)}`;
  const departure = `${startDate}T00:00:00`;
  return candidate < departure ? candidate : departure;
}

/**
 * 안건 하나: 선택지별 표와 투표한 사람, 투표, 예산 선지 추가·삭제, 다음 회차, 댓글
 *
 * @param latestRound 같은 주제(자유 안건은 같은 제목)의 가장 최근 회차. 이 카드보다 크면 이미 다음 회차가 열린 지난 회차다
 */
export function AgendaCard({ item, latestRound }: { item: AgendaListItem; latestRound?: number }) {
  const { roomId, room, myMember } = useRoom();
  // 다른 멤버가 선지를 추가·삭제하면 표 수가 그대로라 실시간 신호(vote.updated)로 다시 불러온다. 이 안건의 신호에만 반응한다
  const detail = useApi(`agenda:${roomId}:${item.agendaId}`, () => api.agenda(roomId, item.agendaId), {
    live: true,
    topic: topics.agenda(item.agendaId),
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [amount, setAmount] = useState("");
  const { confirm, dialog, cancel } = useConfirm();

  const a: AgendaDetailResponse | undefined = detail.data;
  const isFinal = item.subject === "FINAL_PLAN";
  const isCustom = item.subject === "CUSTOM";
  // 자유 안건은 일정을 짜는 중(PLANNING)에도 투표하고 다음 회차를 열 수 있다
  const roomAllowsVote =
    room.status === "SURVEYING" || room.status === "REVIEWING" || (isCustom && room.status === "PLANNING");
  const roomAllowsReopen = room.status === "SURVEYING" || (isCustom && room.status === "PLANNING");
  const canVote = Boolean(a && a.status === "VOTING" && a.eligible && roomAllowsVote);
  const reopened = latestRound !== undefined && latestRound > item.round;
  const isSurveySubject = item.subject === "BUDGET" || item.subject === "STAY" || item.subject === "PACE";
  // 마감이 출발일 0시로 줄어든 안건은 마감 때 미결로 끝나지 않고 출발일 0시 최종 집계로 정해진다
  const endsAtDeparture = item.deadlineAt.startsWith(`${room.startDate}T00:00`);

  // 확인창이 떠 있는 동안 다른 멤버의 표로 안건이 끝나면 물어보던 일이 의미 없어져 닫는다
  useEffect(() => {
    if (a?.status && a.status !== "VOTING") cancel();
  }, [a?.status, cancel]);

  async function run(action: () => Promise<AgendaDetailResponse | void>) {
    setBusy(true);
    setError(null);
    try {
      const next = await action();
      if (next) detail.setData(next);
      // 이 카드는 응답으로 바꿨으니 목록 같은 화면 공통 데이터만 다시 부른다. 다른 카드는 건드리지 않는다
      requestRefresh([]);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function vote(optionId: number, value: string) {
    if (isFinal && value === "REJECT") {
      setRejectOpen(true);
      return;
    }
    // 내 표로 모두 같은 걸 고르게 되면 그 자리에서 정해져 되돌릴 수 없어 먼저 묻는다
    const option = a?.options.find((o) => o.optionId === optionId);
    if (option && a && a.myOptionId !== optionId && option.voteCount === a.totalVoter - 1) {
      const ok = await confirm(
        isFinal
          ? {
              title: "동의할까요?",
              body: "마지막 동의라서 누르면 바로 일정이 확정돼요",
              notes: ["확정된 뒤에는 장소와 일정을 고칠 수 없어요"],
              confirmLabel: "동의하고 확정하기",
            }
          : {
              title: "마지막 표예요",
              body: `${option.label}에 투표하면 모두 같은 걸 골라서 바로 정해져요`,
              notes: ["정해진 뒤에는 표를 바꿀 수 없어요"],
              confirmLabel: "고르고 정하기",
            },
      );
      if (!ok) return;
    }
    void run(() => api.vote(roomId, item.agendaId, optionId));
  }

  async function deleteOption(optionId: number, label: string, mine: boolean) {
    const ok = await confirm({
      title: `${label} 선지를 지울까요?`,
      body: "지운 선지는 되돌릴 수 없어요",
      notes: mine ? ["이 선지에 넣은 내 표도 같이 지워져요. 다른 선지에 다시 투표해 주세요"] : undefined,
      confirmLabel: "지우기",
    });
    if (!ok) return;
    void run(() => api.deleteOption(roomId, item.agendaId, optionId));
  }

  async function openNextRound() {
    const ok = await confirm({
      title: `${item.round + 1}회차 투표를 열까요?`,
      body: `모두 처음부터 다시 골라요. ${dateTimeLabel(nextRoundDeadline(room.startDate))}까지 정해야 해요`,
      notes: ["이번 회차의 표는 다음 회차로 넘어가지 않아요"],
      confirmLabel: "다음 회차 열기",
    });
    if (!ok) return;
    // 응답은 새 회차 안건이라 이 카드에 넣지 않는다. 목록을 다시 불러와 새 카드가 생긴다
    void run(async () => {
      await api.nextRound(roomId, item.agendaId);
    });
  }

  const rejectOption = a?.options.find((o) => o.value === "REJECT");
  // 예산은 멤버가 추가한 선지가 뒤에 붙어 섞이므로 금액순으로 보여 준다. value가 원 단위 금액이다
  const options = a
    ? item.subject === "BUDGET"
      ? [...a.options].sort((x, y) => Number(x.value) - Number(y.value))
      : a.options
    : [];
  // 설문 답이 하나뿐이라 투표 없이 바로 정해진 안건
  const bySurvey = item.status === "RESOLVED" && a !== undefined && a.options.every((o) => o.voteCount === 0);

  return (
    <section className="card" aria-labelledby={`agenda-${item.agendaId}`}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2 className="h2" id={`agenda-${item.agendaId}`}>
          {item.title}
          {item.round > 1 && <span className="small muted" style={{ fontWeight: 400 }}> {item.round}회차</span>}
        </h2>
        {item.status === "VOTING" && (
          <span className="tag">
            {item.votedCount} / {item.totalVoter}명 투표, {remaining(item.deadlineAt)}
          </span>
        )}
        {item.status === "RESOLVED" && <span className="tag ok">정해졌어요: {item.resolvedOption?.label}</span>}
        {item.status === "EXPIRED" && <span className="tag n">마감됐는데 못 정했어요</span>}
      </div>
      {item.status === "VOTING" && (
        <p className="small muted">
          {dateTimeLabel(item.deadlineAt)}까지{" "}
          {isFinal
            ? "모두 동의하면 바로 확정되고, 장소와 일정을 고칠 수 없어요. 한 명이라도 반대하면 투표가 바로 끝나요"
            : "모두 같은 걸 고르면 마지막 표가 들어오는 순간 정해지고, 그 뒤에는 바꿀 수 없어요"}
          {isSurveySubject &&
            (endsAtDeparture
              ? ". 출발일 0시까지 못 정하면 가장 많이 고른 걸로 정해지고, 동점이거나 표가 없으면 방이 닫혀요"
              : ". 마감까지 못 정하면 다음 회차를 열어 다시 골라요")}
          {!item.eligible && ". 안건이 열린 뒤에 들어와서 이번 투표에는 참여할 수 없어요"}
        </p>
      )}

      {bySurvey && <p className="small muted">설문에서 모두 같은 답을 골라 투표 없이 정해졌어요</p>}

      {detail.loading && <p className="small muted">불러오는 중이에요</p>}
      {a && (
        <div className="opts">
          {options.map((o) => {
            const mine = a.myOptionId === o.optionId;
            const ownOption = o.proposedBy && myMember && o.proposedBy.memberId === myMember.memberId;
            return (
              <div key={o.optionId} style={{ position: "relative", display: "flex" }}>
                <button
                  type="button"
                  className="opt grow"
                  aria-pressed={mine}
                  disabled={!canVote || busy}
                  onClick={() => void vote(o.optionId, o.value)}
                >
                  <span className="nm">{o.label}</span>
                  <span className="who">
                    {o.voters.length > 0 && <Faces names={o.voters.map((v) => v.name)} />}
                    <span className="cnt" style={{ color: mine ? "var(--accent)" : undefined }}>
                      {o.voteCount}
                    </span>
                  </span>
                  <span className="k">
                    {mine
                      ? "내 표"
                      : o.voters.length > 0
                        ? o.voters.map((v) => v.name).join(", ")
                        : a.status === "VOTING"
                          ? "아직 아무도 안 골랐어요"
                          : "고른 사람이 없어요"}
                    {o.proposedBy && `, ${o.proposedBy.name}님이 추가`}
                  </span>
                </button>
                {ownOption && room.status === "SURVEYING" && a.status === "VOTING" && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    style={{ position: "absolute", top: 6, right: 6 }}
                    disabled={busy}
                    onClick={() => deleteOption(o.optionId, o.label, mine)}
                    aria-label={`${o.label} 선지 지우기`}
                  >
                    지우기
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {rejectOpen && rejectOption && (
        <form
          className="stack"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!rejectReason.trim()) return;
            const ok = await confirm({
              title: "반대할까요?",
              body: "반대하면 최종 투표가 바로 끝나고 모두 일정 짜기로 돌아가요",
              notes: ["다른 사람이 이미 동의했어도 확정되지 않아요. 일정을 고친 뒤 최종 투표를 다시 열어야 해요"],
              confirmLabel: "반대하기",
            });
            if (!ok) return;
            void run(async () => {
              const res = await api.vote(roomId, item.agendaId, rejectOption.optionId, rejectReason.trim());
              setRejectOpen(false);
              setRejectReason("");
              return res;
            });
          }}
        >
          <label htmlFor={`reject-${item.agendaId}`} className="small">
            반대하는 이유를 남겨 주세요. 친구들이 보고 일정을 고칠 수 있어요
          </label>
          <textarea
            id={`reject-${item.agendaId}`}
            className="in"
            rows={2}
            maxLength={500}
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
          <div className="row">
            <button type="submit" className="btn btn-accent btn-sm" disabled={busy || !rejectReason.trim()}>
              반대하기
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setRejectOpen(false)}>
              그만두기
            </button>
          </div>
        </form>
      )}

      {a && item.subject === "BUDGET" && a.status === "VOTING" && room.status === "SURVEYING" && (
        <form
          className="row"
          onSubmit={(e) => {
            e.preventDefault();
            const won = Math.round(Number(amount) * 10_000);
            if (!Number.isFinite(won) || won < 1 || won > BUDGET_MAX) {
              setError("예산은 1만원부터 9,999만원까지 넣을 수 있어요.");
              return;
            }
            void run(async () => {
              const res = await api.addBudgetOption(roomId, item.agendaId, won);
              setAmount("");
              return res;
            });
          }}
        >
          <label htmlFor={`amount-${item.agendaId}`} className="sr-only">
            새 예산 선지 (만원)
          </label>
          <input
            id={`amount-${item.agendaId}`}
            className="in grow"
            style={{ maxWidth: 200 }}
            inputMode="numeric"
            placeholder="예: 150"
            value={amount}
            onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ""))}
          />
          <span className="muted">만원</span>
          <button type="submit" className="btn btn-line btn-sm" disabled={busy || !amount}>
            선지 추가
          </button>
        </form>
      )}

      {item.status === "EXPIRED" && !isFinal && (roomAllowsReopen || reopened) && (
        <div className="row">
          <button
            type="button"
            className="btn btn-accent btn-sm"
            disabled={busy || reopened}
            aria-describedby={`round-note-${item.agendaId}`}
            onClick={() => void openNextRound()}
          >
            다음 회차 투표 열기
          </button>
          <span id={`round-note-${item.agendaId}`} className="small muted">
            {reopened
              ? `이 회차는 의견이 갈려서 못 정했어요. 이미 ${latestRound}회차 투표가 열렸어요`
              : isCustom
                ? // 자유 안건은 최종 투표를 열어 끝났을 수도 있어 이유를 말하지 않는다
                  "이번에는 못 정하고 끝났어요. 다시 골라 봐요"
                : "이번에는 의견이 갈려서 못 정했어요. 다시 골라 봐요"}
          </span>
        </div>
      )}

      {error && <Notice kind="err">{error}</Notice>}
      <AgendaComments agendaId={item.agendaId} closed={item.status !== "VOTING"} />
      {dialog}
    </section>
  );
}

/** @param closed 투표가 끝난(정해졌거나 못 정하고 마감된) 안건이면 지난 의견은 보여 주되 새 의견은 받지 않는다. 이어서 할 이야기는 다음 회차에서 한다 */
function AgendaComments({ agendaId, closed }: { agendaId: number; closed: boolean }) {
  const { roomId, room } = useRoom();
  const comments = useApi(`agenda-comments:${roomId}:${agendaId}`, () => api.agendaComments(roomId, agendaId), {
    live: true,
    topic: topics.agendaComments(agendaId),
  });
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);
  const canWrite = room.status === "SURVEYING" || room.status === "PLANNING" || room.status === "REVIEWING";
  const list = comments.data?.comments ?? [];

  return (
    <div className="stack" style={{ paddingTop: 14, borderTop: "1px solid var(--line)" }}>
      <CommentList comments={list} />
      {canWrite && (
        <form
          className="row"
          style={{ flexWrap: "nowrap" }}
          onSubmit={async (e) => {
            e.preventDefault();
            if (!body.trim()) return;
            setError(null);
            try {
              const c = await api.addAgendaComment(roomId, agendaId, body.trim());
              comments.setData({ comments: [...list, c] });
              setBody("");
            } catch (err) {
              setError(err instanceof ApiError ? err.message : errorMessage(err));
            }
          }}
        >
          <label htmlFor={`c-${agendaId}`} className="sr-only">
            의견 남기기
          </label>
          <input
            id={`c-${agendaId}`}
            className="in grow"
            maxLength={500}
            placeholder={closed ? "끝난 안건에는 의견을 남길 수 없어요" : "의견 남기기"}
            disabled={closed}
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
          <button type="submit" className="btn btn-accent" disabled={closed || !body.trim()}>
            남기기
          </button>
        </form>
      )}
      {error && <Notice kind="err">{error}</Notice>}
    </div>
  );
}

/** 안건 의견 목록. 같이 정하기 카드와 일정 화면의 지난 최종 확정 카드가 같이 쓴다 */
export function CommentList({ comments }: { comments: CommentResponse[] }) {
  return (
    <>
      {comments.map((c) => (
        <div key={c.commentId} className="say">
          <Avatar name={c.name} size="s" />
          <p>
            <b>{c.name}</b>
            <small>{feedTime(c.createdAt)}</small>
            <br />
            {c.body}
          </p>
        </div>
      ))}
    </>
  );
}
