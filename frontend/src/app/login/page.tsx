"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { safeNext } from "@/lib/hooks";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.login(email.trim(), password);
      router.replace(next);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <AuthShell
      photo="umeda"
      big={
        <>
          혼자 짜지 않아도
          <br />
          되는 여행
        </>
      }
      sub="친구들 의견을 먼저 모으고, 갈리는 것만 같이 정해요"
    >
      <div>
        <h1>다시 만나서 반가워요</h1>
        <p className="muted" style={{ marginTop: 6 }}>
          로그인하고 준비 중인 여행으로 돌아가요
        </p>
      </div>
      <form onSubmit={onSubmit} className="stack" style={{ gap: 16 }}>
        <div className="f">
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            className="in"
            type="email"
            autoComplete="email"
            placeholder="name@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="f">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            className="in"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && <Notice kind="err">{error}</Notice>}
        <button type="submit" className="btn btn-accent btn-lg" disabled={busy}>
          {busy ? "로그인하는 중" : "로그인"}
        </button>
      </form>
      <p className="muted" style={{ textAlign: "center" }}>
        처음이신가요? <Link href={`/signup?next=${encodeURIComponent(next)}`}>회원가입</Link>
      </p>
    </AuthShell>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
