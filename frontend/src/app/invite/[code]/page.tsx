"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { Cover, Loading, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { dDay, dateLabel, tripLength } from "@/lib/format";
import { useApi } from "@/lib/hooks";

export default function InvitePage() {
  const { code } = useParams<{ code: string }>();
  const router = useRouter();
  const preview = useApi(`invite:${code}`, () => api.invitePreview(code));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const here = `/invite/${encodeURIComponent(code)}`;

  async function join() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.join(code);
      router.replace(res.surveyRequired ? `/rooms/${res.roomId}/survey` : `/rooms/${res.roomId}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        router.replace(`/login?next=${encodeURIComponent(here)}`);
        return;
      }
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  const p = preview.data;
  const statusNotice = p && {
    SURVEYING: "설문 진행 중입니다.",
    PLANNING: "일정 조율 중입니다. 하고 싶은 것과 피할 것 설문을 남겨주세요!",
    REVIEWING: "일정 조율 완료! 지금 최종 일정 투표를 진행하고 있어요.",
    FINALIZED: "모든 일정이 최종 확정되었습니다. 확정된 계획표를 확인하세요!",
    EXPIRED: "만료된 초대입니다.",
  }[p.status];
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: "32px 16px" }}>
      <main className="card" style={{ width: "100%", maxWidth: 520, padding: 0, overflow: "hidden", gap: 0 }}>
        {preview.loading && <Loading />}
        {preview.error && (
          <div className="stack" style={{ padding: 28 }}>
            <h1 style={{ margin: 0, fontSize: 26 }}>초대를 찾지 못했어요</h1>
            <p className="muted">{preview.error.code === "INVITE_CODE_NOT_FOUND" ? "코드가 맞는지 다시 확인해 주세요." : preview.error.message}</p>
            <Link href="/rooms" className="btn btn-line">
              내 여행방으로
            </Link>
          </div>
        )}
        {p && (
          <>
            <Cover destination={p.destination} style={{ height: 160, fontSize: 56 }} />
            <div className="stack" style={{ padding: "24px 28px 28px", gap: 20 }}>
              <div className="stack" style={{ gap: 6 }}>
                <span className="small muted">착착 여행방 초대</span>
                <h1 style={{ margin: 0, fontSize: 30 }}>{p.name}</h1>
                <p>
                  {p.destination}, {dateLabel(p.startDate)}부터 {tripLength(p.startDate, p.endDate)}, <b>{dDay(p.startDate, p.endDate)}</b>
                </p>
                <p className="small muted">지금 {p.memberCount}명이 기다리고 있어요</p>
              </div>
              {statusNotice && <Notice kind="warn">{statusNotice}</Notice>}
              <ol className="stack" style={{ margin: 0, paddingLeft: 20, gap: 8 }}>
                <li>
                  <b>1분 설문부터 해요</b>
                  <br />
                  <span className="small muted">예산, 숙소, 하루 속도, 하고 싶은 것, 피하고 싶은 것</span>
                </li>
                <li>
                  <b>갈린 것만 같이 정해요</b>
                </li>
                <li>
                  <b>가고 싶은 곳을 모아 일정을 짜요</b>
                  <br />
                  <span className="small muted">모두 동의하면 확정돼요</span>
                </li>
              </ol>
              {error && <Notice kind="err">{error}</Notice>}
              {p.notJoinableReason === "LOGIN_REQUIRED" && (
                <div className="stack" style={{ gap: 8 }}>
                  <Link className="btn btn-accent btn-lg" href={`/login?next=${encodeURIComponent(here)}`}>
                    로그인하고 참여하기
                  </Link>
                  <Link className="btn btn-line btn-lg" href={`/signup?next=${encodeURIComponent(here)}`}>
                    처음이에요, 가입하고 참여하기
                  </Link>
                </div>
              )}
              {p.notJoinableReason === "ALREADY_MEMBER" && (
                <Link className="btn btn-accent btn-lg" href={`/rooms/${p.roomId}`}>
                  이미 참여한 방이에요. 들어가기
                </Link>
              )}
              {p.notJoinableReason === "REJOIN_NOT_ALLOWED" && (
                <Notice kind="warn">이 방에서 나간 적이 있어서 다시 들어갈 수 없어요.</Notice>
              )}
              {p.joinable && (
                <button type="button" className="btn btn-accent btn-lg" onClick={join} disabled={busy}>
                  {busy ? "참여하는 중" : "참여하기"}
                </button>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
