"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ThemeToggle } from "@/components/ui";
import { api } from "@/lib/api";

/** 방 밖 화면의 위 막대 */
export function TopBar({ loggedIn }: { loggedIn: boolean }) {
  const router = useRouter();
  return (
    <header className="top">
      <div className="top-in">
        <Link href={loggedIn ? "/rooms" : "/"} className="brand" aria-label="착착">
          착착
        </Link>
        <ThemeToggle />
        {loggedIn ? (
          <>
            <Link href="/rooms/new" className="btn btn-accent btn-sm">
              새 여행방
            </Link>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={async () => {
                await api.logout().catch(() => undefined);
                router.replace("/login");
              }}
            >
              로그아웃
            </button>
          </>
        ) : (
          <>
            <Link href="/login" className="btn btn-line btn-sm">
              로그인
            </Link>
            <Link href="/signup" className="btn btn-accent btn-sm">
              시작하기
            </Link>
          </>
        )}
      </div>
    </header>
  );
}
