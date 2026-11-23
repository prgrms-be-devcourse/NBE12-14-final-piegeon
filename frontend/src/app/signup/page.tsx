"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { safeNext } from "@/lib/hooks";

// 백엔드 규칙: 8~64자, 공백 없는 영문·숫자·기호
const PASSWORD_RULE = /^[\x21-\x7E]{8,64}$/;

function SignupForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!PASSWORD_RULE.test(password)) {
      setError("비밀번호는 공백 없이 영문, 숫자, 기호로 8자 이상 써 주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.signup(email.trim(), name.trim(), password);
      // 가입은 로그인을 해 주지 않아서 바로 이어서 로그인한다
      await api.login(email.trim(), password);
      router.replace(next);
    } catch (err) {
      if (err instanceof ApiError && err.code === "INVALID_INPUT") {
        setError("입력한 내용을 다시 확인해 주세요. 이름은 50자, 이메일은 형식에 맞게 써 주세요.");
      } else {
        setError(errorMessage(err));
      }
      setBusy(false);
    }
  }

  return (
    <AuthShell
      big={
        <>
          친구들 취향이
          <br />
          착착 맞는 여행
        </>
      }
      sub="가입하고 첫 여행방을 만들거나, 받은 초대로 들어가요"
    >
      <div>
        <h1>착착 시작하기</h1>
        <p className="muted" style={{ marginTop: 6 }}>
          이메일로 가입해요
        </p>
      </div>
      <form onSubmit={onSubmit} className="stack" style={{ gap: 16 }}>
        <div className="f">
          <label htmlFor="name">이름</label>
          <input
            id="name"
            className="in"
            autoComplete="name"
            placeholder="친구들에게 보일 이름"
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <span className="help">여행방에서 이 이름과 첫 글자가 보여요</span>
        </div>
        <div className="f">
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            className="in"
            type="email"
            autoComplete="email"
            placeholder="name@example.com"
            maxLength={255}
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
            autoComplete="new-password"
            placeholder="8자 이상"
            maxLength={64}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <span className="help">공백 없이 영문, 숫자, 기호로 8자 이상</span>
        </div>
        {error && <Notice kind="err">{error}</Notice>}
        <button type="submit" className="btn btn-accent btn-lg" disabled={busy}>
          {busy ? "가입하는 중" : "가입하기"}
        </button>
      </form>
      <p className="muted" style={{ textAlign: "center" }}>
        이미 계정이 있나요? <Link href={`/login?next=${encodeURIComponent(next)}`}>로그인</Link>
      </p>
    </AuthShell>
  );
}

export default function SignupPage() {
  return (
    <Suspense>
      <SignupForm />
    </Suspense>
  );
}
