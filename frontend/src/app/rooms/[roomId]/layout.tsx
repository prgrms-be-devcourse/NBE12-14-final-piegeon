"use client";

import Link from "next/link";
import { useParams, usePathname, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { RoomProvider, findMyMember, useRoomEvents } from "@/components/room";
import { Cover, CopyButton, Loading, Notice, ThemeToggle } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { dDay, shortDate, tripLength } from "@/lib/format";
import { useApi, useMe } from "@/lib/hooks";

export default function RoomLayout({ children }: { children: React.ReactNode }) {
  const { roomId } = useParams<{ roomId: string }>();
  const pathname = usePathname();
  const router = useRouter();
  const me = useMe();
  const room = useApi(`room:${roomId}`, () => api.room(roomId), { live: true });
  const agendas = useApi(`agendas:${roomId}`, () => api.agendas(roomId), { live: true });
  const [leaveError, setLeaveError] = useState<string | null>(null);
  useRoomEvents(roomId, Boolean(room.data));

  const value = useMemo(() => {
    if (!room.data || !me.data) return null;
    return {
      roomId,
      room: room.data,
      me: me.data,
      myMember: findMyMember(room.data, me.data),
      reloadRoom: room.reload,
    };
  }, [roomId, room.data, room.reload, me.data]);

  if (room.error) {
    return (
      <main className="page" style={{ maxWidth: 560 }}>
        <Notice kind="err">
          {room.error.code === "FORBIDDEN" ? "이 방의 멤버가 아니에요. 초대 링크로 들어와 주세요." : room.error.message}
        </Notice>
        <Link href="/rooms" className="btn btn-line">
          내 여행방으로
        </Link>
      </main>
    );
  }
  if (!value) return <Loading />;

  const r = value.room;
  const base = `/rooms/${roomId}`;
  const myTurn = (agendas.data?.agendas ?? []).filter(
    (a) => a.status === "VOTING" && a.eligible && a.myOptionId === null && a.subject !== "FINAL_PLAN",
  ).length;
  const nav: { href: string; label: string; badge?: number }[] = [
    { href: base, label: "여행 홈" },
    { href: `${base}/survey`, label: "설문" },
    { href: `${base}/vote`, label: "같이 정하기", badge: myTurn },
    { href: `${base}/places`, label: "가고 싶은 곳" },
    { href: `${base}/schedule`, label: "일정" },
    { href: `${base}/news`, label: "새 소식" },
  ];
  if (r.status === "FINALIZED") nav.splice(5, 0, { href: `${base}/plan`, label: "확정된 계획" });
  const inviteUrl = typeof window === "undefined" ? "" : `${window.location.origin}/invite/${r.inviteCode}`;

  return (
    <RoomProvider value={value}>
      <div className="app">
        <aside className="rail" aria-label="여행방">
          <div className="brand-row">
            <Link className="brand" href="/rooms" aria-label="착착, 내 여행방으로">
              착착
            </Link>
            <Link className="back" href="/rooms">
              내 여행방
            </Link>
          </div>
          <div className="trip">
            <Cover destination={r.destination} className="cover-mini" style={{ fontSize: 22 }} />
            <span>
              <b>{r.name}</b>
              <small>
                {shortDate(r.startDate)}부터 {tripLength(r.startDate, r.endDate)}
              </small>
              <em>{dDay(r.startDate, r.endDate)}</em>
            </span>
          </div>
          <nav aria-label="여행방 메뉴">
            {nav.map((n) => (
              <Link key={n.href} className="nav" href={n.href} aria-current={pathname === n.href ? "page" : undefined}>
                {n.label}
                {n.badge ? <span className="badge" aria-label={`${n.badge}개 남음`}>{n.badge}</span> : null}
              </Link>
            ))}
          </nav>
          <div className="foot">
            <CopyButton text={inviteUrl} label="초대 링크 복사" />
            <span className="small muted" style={{ padding: "0 4px" }}>
              초대 코드 <b style={{ color: "var(--ink)", letterSpacing: "0.06em" }}>{r.inviteCode}</b>
            </span>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <ThemeToggle />
              <button
                type="button"
                className="btn btn-ghost"
                onClick={async () => {
                  if (!window.confirm("이 방에서 나갈까요? 나가면 다시 들어올 수 없어요.")) return;
                  try {
                    await api.leave(roomId);
                    router.replace("/rooms");
                  } catch (e) {
                    setLeaveError(errorMessage(e));
                  }
                }}
              >
                방 나가기
              </button>
            </div>
            {leaveError && <Notice kind="err">{leaveError}</Notice>}
          </div>
        </aside>
        <main className="main">{children}</main>
      </div>
    </RoomProvider>
  );
}
