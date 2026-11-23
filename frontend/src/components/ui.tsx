"use client";

import { useState } from "react";
import { initial } from "@/lib/format";
import { useTheme } from "@/lib/hooks";

export function Avatar({ name, size }: { name: string; size?: "s" | "l" }) {
  return (
    <span className={size ? `av ${size}` : "av"} title={name} aria-hidden="true">
      {initial(name)}
    </span>
  );
}

/** 겹쳐 놓은 아바타. 이름은 화면 읽기 프로그램에 한 번에 읽힌다 */
export function Faces({ names, max = 5 }: { names: string[]; max?: number }) {
  const shown = names.slice(0, max);
  return (
    <span className="faces" aria-label={names.join(", ")} role="img">
      {shown.map((n, i) => (
        <Avatar key={`${n}-${i}`} name={n} size="s" />
      ))}
      {names.length > max && <span className="av s">+{names.length - max}</span>}
    </span>
  );
}

/** 여행지 첫 글자로 그린 커버 */
export function Cover({
  destination,
  className,
  style,
}: {
  destination: string;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <span className={`cover ${className ?? ""}`} style={style} aria-hidden="true">
      {initial(destination)}
    </span>
  );
}

export function Notice({
  kind,
  children,
}: {
  kind?: "err" | "ok" | "warn";
  children: React.ReactNode;
}) {
  return (
    <div className={kind ? `notice ${kind}` : "notice"} role={kind === "err" ? "alert" : "status"}>
      {children}
    </div>
  );
}

export function Loading({ label = "불러오는 중이에요" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      {label}
    </div>
  );
}

/** 화이트/블랙 바꾸기. 지금 화이트면 달(블랙으로), 블랙이면 해(화이트로)를 보여 준다 */
export function ThemeToggle() {
  const [theme, toggle] = useTheme();
  const toDark = theme === "light";
  const label = toDark ? "블랙 화면으로 바꾸기" : "화이트 화면으로 바꾸기";
  return (
    <button type="button" className="btn btn-ghost btn-icon" onClick={toggle} aria-label={label} title={label}>
      {toDark ? (
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5z" />
        </svg>
      ) : (
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        </svg>
      )}
    </button>
  );
}

/** 글자를 복사하는 버튼. 복사가 막힌 환경이면 글자를 보여 줘서 직접 복사하게 한다 */
export function CopyButton({ text, label }: { text: string; label: string }) {
  const [state, setState] = useState<"idle" | "done" | "fail">("idle");
  return (
    <span className="stack" style={{ gap: 6 }}>
      <button
        type="button"
        className="btn btn-line"
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(text);
            setState("done");
            setTimeout(() => setState("idle"), 1600);
          } catch {
            setState("fail");
          }
        }}
      >
        {state === "done" ? "복사했어요" : label}
      </button>
      {state === "fail" && (
        <input className="in" readOnly value={text} onFocus={(e) => e.currentTarget.select()} aria-label={label} />
      )}
    </span>
  );
}
