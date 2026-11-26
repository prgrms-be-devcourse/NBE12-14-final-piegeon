"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthShell } from "@/components/AuthShell";
import { Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { safeNext } from "@/lib/hooks";

// 백엔드 SignupRequest와 같은 규칙. 서버까지 가기 전에 칸마다 먼저 알려 준다.
const EMAIL_RULE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_CHARS = /^[\x21-\x7E]*$/;

type Field = "name" | "email" | "password";
type Errors = Partial<Record<Field, string>>;

function validate(name: string, email: string, password: string): Errors {
  const errors: Errors = {};
  const n = name.trim();
  const m = email.trim();
  if (!n) errors.name = "이름을 입력해 주세요.";
  else if (n.length > 50) errors.name = "이름은 50자 이하로 입력해 주세요.";
  if (!m) errors.email = "이메일을 입력해 주세요.";
  else if (m.length > 255) errors.email = "이메일은 255자 이하로 입력해 주세요.";
  else if (!EMAIL_RULE.test(m)) errors.email = "이메일 형식이 아니에요. 예: name@example.com";
  if (!password) errors.password = "비밀번호를 입력해 주세요.";
  else if (!PASSWORD_CHARS.test(password)) errors.password = "비밀번호는 공백 없이 영문, 숫자, 기호만 쓸 수 있어요.";
  else if (password.length < 8 || password.length > 64) errors.password = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.";
  return errors;
}

const FIELD_ORDER: Field[] = ["name", "email", "password"];

const INVALID_INPUT: React.CSSProperties = { borderColor: "var(--accent-strong)" };

function FieldMessage({ id, error, help }: { id: string; error?: string; help?: string }) {
  if (error) {
    return (
      <span id={id} className="help" role="alert" style={{ color: "var(--accent-strong)" }}>
        {error}
      </span>
    );
  }
  return help ? (
    <span id={id} className="help">
      {help}
    </span>
  ) : null;
}

function SignupForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // 한 번 벗어난 칸만 에러를 보여 준다. 가입하기를 누르면 모든 칸을 보여 준다.
  const [touched, setTouched] = useState<Partial<Record<Field, boolean>>>({});

  const errors = validate(name, email, password);
  const shown = (f: Field) => (touched[f] ? errors[f] : undefined);
  const touch = (f: Field) => setTouched((t) => ({ ...t, [f]: true }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched({ name: true, email: true, password: true });
    const first = FIELD_ORDER.find((f) => errors[f]);
    if (first) {
      document.getElementById(first)?.focus();
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
      photo="kuromon"
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
      <form onSubmit={onSubmit} className="stack" style={{ gap: 16 }} noValidate>
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
            onBlur={() => touch("name")}
            aria-invalid={!!shown("name")}
            aria-describedby="name-msg"
            style={shown("name") ? INVALID_INPUT : undefined}
            required
          />
          <FieldMessage id="name-msg" error={shown("name")} help="여행방에서 이 이름과 첫 글자가 보여요" />
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
            onBlur={() => touch("email")}
            aria-invalid={!!shown("email")}
            aria-describedby="email-msg"
            style={shown("email") ? INVALID_INPUT : undefined}
            required
          />
          <FieldMessage id="email-msg" error={shown("email")} />
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
            onChange={(e) => {
              setPassword(e.target.value);
              // 비밀번호는 치는 동안 바로 규칙을 알려 준다
              if (e.target.value) touch("password");
            }}
            onBlur={() => touch("password")}
            aria-invalid={!!shown("password")}
            aria-describedby="password-msg"
            style={shown("password") ? INVALID_INPUT : undefined}
            required
          />
          <FieldMessage id="password-msg" error={shown("password")} help="공백 없이 영문, 숫자, 기호로 8자 이상" />
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
