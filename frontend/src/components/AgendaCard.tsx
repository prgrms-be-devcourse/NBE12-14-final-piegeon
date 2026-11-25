"use client";

import { useState } from "react";
import { useRoom } from "@/components/room";
import { Avatar, Faces, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { dateTimeLabel, feedTime, remaining } from "@/lib/format";
import { requestRefresh, topics, useApi } from "@/lib/hooks";
import type { AgendaDetailResponse, AgendaListItem, CommentResponse } from "@/lib/types";

const BUDGET_MAX = 99_999_999;

/**
 * 안건 하나: 선택지별 표와 투표한 사람, 투표, 예산 선지 추가·삭제, 다음 회차, 댓글
 *
 * @param latestRound 같은 주제의 가장 최근 회차. 이 카드보다 크면 이미 다음 회차가 열린 지난 회차다
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

  const a: AgendaDetailResponse | undefined = detail.data;
  const isFinal = item.subject === "FINAL_PLAN";
  const roomAllowsVote = room.status === "SURVEYING" || room.status === "REVIEWING";
  const canVote = Boolean(a && a.status === "VOTING" && a.eligible && roomAllowsVote);
  const reopened = latestRound !== undefined && latestRound > item.round;

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

  function vote(optionId: number, value: string) {
    if (isFinal && value === "REJECT") {
      setRejectOpen(true);
      return;
    }
    void run(() => api.vote(roomId, item.agendaId, optionId));
  }

  const rejectOption = a?.options.find((o) => o.value === "REJECT");

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
          {dateTimeLabel(item.deadlineAt)}까지 {isFinal ? "모두 동의하면 일정이 확정돼요" : "모두 같은 걸 고르면 정해져요"}
          {!item.eligible && ". 안건이 열린 뒤에 들어와서 이번 투표에는 참여할 수 없어요"}
        </p>
      )}

      {detail.loading && <p className="small muted">불러오는 중이에요</p>}
      {a && (
        <div className="opts">
          {a.options.map((o) => {
            const mine = a.myOptionId === o.optionId;
            const ownOption = o.proposedBy && myMember && o.proposedBy.memberId === myMember.memberId;
            return (
              <div key={o.optionId} style={{ position: "relative", display: "flex" }}>
                <button
                  type="button"
                  className="opt grow"
                  aria-pressed={mine}
                  disabled={!canVote || busy}
                  onClick={() => vote(o.optionId, o.value)}
                >
                  <span className="nm">{o.label}</span>
                  <span className="who">
                    {o.voters.length > 0 && <Faces names={o.voters.map((v) => v.name)} />}
                    <span className="cnt" style={{ color: mine ? "var(--accent)" : undefined }}>
                      {o.voteCount}
                    </span>
                  </span>
                  <span className="k">
                    {mine ? "내 표" : o.voters.length === 0 ? "아직 아무도 안 골랐어요" : `${o.voters.map((v) => v.name).join(", ")}`}
                    {o.proposedBy && `, ${o.proposedBy.name}님이 추가`}
                  </span>
                </button>
                {ownOption && room.status === "SURVEYING" && a.status === "VOTING" && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    style={{ position: "absolute", top: 6, right: 6 }}
                    disabled={busy}
                    onClick={() => run(() => api.deleteOption(roomId, item.agendaId, o.optionId))}
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
          onSubmit={(e) => {
            e.preventDefault();
            if (!rejectReason.trim()) return;
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

      {item.status === "EXPIRED" && !isFinal && (room.status === "SURVEYING" || reopened) && (
        <div className="row">
          <button
            type="button"
            className="btn btn-accent btn-sm"
            disabled={busy || reopened}
            aria-describedby={`round-note-${item.agendaId}`}
            onClick={() =>
              // 응답은 새 회차 안건이라 이 카드에 넣지 않는다. 목록을 다시 불러와 새 카드가 생긴다
              run(async () => {
                await api.nextRound(roomId, item.agendaId);
              })
            }
          >
            다음 회차 투표 열기
          </button>
          <span id={`round-note-${item.agendaId}`} className="small muted">
            {reopened
              ? `이 회차는 의견이 갈려서 못 정했어요. 이미 ${latestRound}회차 투표가 열렸어요`
              : "이번에는 의견이 갈려서 못 정했어요. 다시 골라 봐요"}
          </span>
        </div>
      )}

      {error && <Notice kind="err">{error}</Notice>}
      <AgendaComments agendaId={item.agendaId} />
    </section>
  );
}

function AgendaComments({ agendaId }: { agendaId: number }) {
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
          <input id={`c-${agendaId}`} className="in grow" maxLength={500} placeholder="의견 남기기" value={body} onChange={(e) => setBody(e.target.value)} />
          <button type="submit" className="btn btn-accent" disabled={!body.trim()}>
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
