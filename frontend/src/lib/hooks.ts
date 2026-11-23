"use client";

import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, api } from "./api";
import type { UserResponse } from "./types";

// 다른 사람의 변경이 왔을 때(SSE) 화면 데이터를 다시 불러오라는 신호
const REFRESH_EVENT = "chakchak:refresh";

export function requestRefresh() {
  window.dispatchEvent(new Event(REFRESH_EVENT));
}

export interface ApiState<T> {
  data: T | undefined;
  error: ApiError | null;
  loading: boolean;
  reload: () => Promise<void>;
  setData: (next: T) => void;
}

/**
 * fetcher를 불러 결과를 들고 있는다. key가 바뀌면 다시 부르고, null이면 부르지 않는다.
 * live가 켜져 있으면 실시간 신호와 창으로 돌아왔을 때도 다시 부른다.
 * 로그인이 풀렸으면(401) 로그인 화면으로 보낸다.
 */
export function useApi<T>(
  key: string | null,
  fetcher: () => Promise<T>,
  options: { live?: boolean } = {},
): ApiState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(key !== null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const toLogin = useLoginRedirect();

  const reload = useCallback(async () => {
    if (key === null) return;
    try {
      const next = await fetcherRef.current();
      setData(next);
      setError(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        toLogin();
        return;
      }
      setError(e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "불러오지 못했어요."));
    } finally {
      setLoading(false);
    }
  }, [key, toLogin]);

  useEffect(() => {
    setLoading(key !== null);
    setData(undefined);
    void reload();
  }, [key, reload]);

  useEffect(() => {
    if (!options.live || key === null) return;
    const onRefresh = () => void reload();
    const onVisible = () => {
      if (document.visibilityState === "visible") void reload();
    };
    window.addEventListener(REFRESH_EVENT, onRefresh);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener(REFRESH_EVENT, onRefresh);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [options.live, key, reload]);

  return { data, error, loading, reload, setData };
}

/** 지금 주소를 기억해 두고 로그인 화면으로 보낸다. 로그인하면 이 주소로 돌아온다 */
export function useLoginRedirect() {
  const router = useRouter();
  const pathname = usePathname();
  return useCallback(() => {
    router.replace(`/login?next=${encodeURIComponent(pathname || "/rooms")}`);
  }, [router, pathname]);
}

/** 로그인한 사용자. 로그인 안 했으면 로그인 화면으로 보낸다 */
export function useMe() {
  return useApi<UserResponse>("me", api.me);
}

/** 로그인 뒤 돌아갈 주소. 다른 사이트로 가지 않게 우리 주소(/로 시작)만 받는다 */
export function safeNext(next: string | null, fallback = "/rooms"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return fallback;
  return next;
}

/** 화이트/블랙. 고르지 않으면 시스템 설정을 따른다 */
export function useTheme(): [string | null, () => void] {
  const [theme, setTheme] = useState<string | null>(null);
  useEffect(() => {
    try {
      setTheme(localStorage.getItem("theme"));
    } catch {
      setTheme(null);
    }
  }, []);
  const toggle = useCallback(() => {
    const current =
      document.documentElement.dataset.theme ??
      (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    const next = current === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem("theme", next);
    } catch {
      // 저장이 막혀 있어도 이번 화면에서는 바뀐다
    }
    setTheme(next);
  }, []);
  return [theme, toggle];
}
