"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { safeNext } from "@/lib/hooks";

// 백엔드 LoginRequest와 같은 규칙. 비밀번호 형식은 가입 때 이미 검사했으니 길이만 본다.
const EMAIL_RULE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Field = "email" | "password";
type Errors = Partial<Record<Field, string>>;

function validate(email: string, password: string): Errors {
  const errors: Errors = {};
  const m = email.trim();
  if (!m) errors.email = "이메일을 입력해 주세요.";
  else if (!EMAIL_RULE.test(m)) errors.email = "이메일 형식이 아니에요. 예: name@example.com";
  if (!password) errors.password = "비밀번호를 입력해 주세요.";
  else if (password.length > 64) errors.password = "비밀번호는 64자 이하로 입력해 주세요.";
  return errors;
}

const FIELD_ORDER: Field[] = ["email", "password"];
const INVALID_INPUT: React.CSSProperties = { borderColor: "var(--accent-strong)" };

function FieldError({ id, error }: { id: string; error?: string }) {
  return error ? (
    <span id={id} className="help" role="alert" style={{ color: "var(--accent-strong)" }}>
      {error}
    </span>
  ) : null;
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // 한 번 벗어난 칸만 에러를 보여 준다. 로그인을 누르면 모든 칸을 보여 준다.
  const [touched, setTouched] = useState<Partial<Record<Field, boolean>>>({});

  const errors = validate(email, password);
  const shown = (f: Field) => (touched[f] ? errors[f] : undefined);
  const touch = (f: Field) => setTouched((t) => ({ ...t, [f]: true }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched({ email: true, password: true });
    const first = FIELD_ORDER.find((f) => errors[f]);
    if (first) {
      document.getElementById(first)?.focus();
      return;
    }
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
      <form onSubmit={onSubmit} className="stack" style={{ gap: 16 }} noValidate>
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
            onBlur={() => touch("email")}
            aria-invalid={!!shown("email")}
            aria-describedby="email-msg"
            style={shown("email") ? INVALID_INPUT : undefined}
            required
          />
          <FieldError id="email-msg" error={shown("email")} />
        </div>
        <div className="f">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            className="in"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호"
            maxLength={64}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onBlur={() => touch("password")}
            aria-invalid={!!shown("password")}
            aria-describedby="password-msg"
            style={shown("password") ? INVALID_INPUT : undefined}
            required
          />
          <FieldError id="password-msg" error={shown("password")} />
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
