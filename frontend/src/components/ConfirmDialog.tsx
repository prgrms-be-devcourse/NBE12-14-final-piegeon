"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";

export interface ConfirmOptions {
  title: string;
  body?: string;
  /** 누르기 전에 꼭 알아야 할 점. 본문 아래에 따로 눈에 띄게 보여 준다 */
  notes?: string[];
  /** 본문 아래에 그릴 추가 내용 (예: 일차 요약) */
  extra?: React.ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
}

/**
 * 브라우저 기본 확인창(window.confirm) 대신 쓰는 확인창.
 * `if (!(await confirm({...}))) return;`처럼 쓰고, 확인을 누르면 true, 취소·Esc·바깥을 누르면 false다.
 * 돌려준 dialog를 화면 어딘가에 그려 둬야 한다. showModal로 열어서 어디에 두든 화면 맨 위에 뜬다.
 */
export function useConfirm() {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((ok: boolean) => void) | null>(null);

  const confirm = useCallback(
    (next: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        // 열려 있던 확인창이 있으면 취소로 끝낸다
        resolver.current?.(false);
        resolver.current = resolve;
        setOptions(next);
      }),
    [],
  );

  const close = useCallback((ok: boolean) => {
    resolver.current?.(ok);
    resolver.current = null;
    setOptions(null);
  }, []);

  const dialog = options ? <ConfirmDialog options={options} onClose={close} /> : null;
  return { confirm, dialog };
}

function ConfirmDialog({ options, onClose }: { options: ConfirmOptions; onClose: (ok: boolean) => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const bodyId = useId();

  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
  }, []);

  return (
    <dialog
      ref={ref}
      className="confirm"
      aria-labelledby={titleId}
      aria-describedby={options.body ? bodyId : undefined}
      // Esc를 누르면 브라우저가 닫기 전에 취소로 끝낸다
      onCancel={(e) => {
        e.preventDefault();
        onClose(false);
      }}
      // 안쪽이 창을 꽉 채워서, dialog 자신이 눌렸으면 바깥 어두운 곳을 누른 것이다
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose(false);
      }}
    >
      <div className="cf-in">
        <h2 id={titleId} className="cf-title">
          {options.title}
        </h2>
        {options.body && (
          <p id={bodyId} className="cf-body">
            {options.body}
          </p>
        )}
        {options.notes && options.notes.length > 0 && (
          <ul className="cf-notes">
            {options.notes.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        )}
        {options.extra}
        <div className="cf-acts">
          {/* 되돌릴 수 없는 일이 많아서, Enter를 바로 눌러도 실행되지 않게 취소에 먼저 초점을 둔다 */}
          <button type="button" className="btn btn-line" autoFocus onClick={() => onClose(false)}>
            {options.cancelLabel ?? "취소"}
          </button>
          <button type="button" className="btn btn-accent" onClick={() => onClose(true)}>
            {options.confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}
