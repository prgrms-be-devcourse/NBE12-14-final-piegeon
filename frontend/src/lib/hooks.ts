"use client";

import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, api } from "./api";
import type { UserResponse } from "./types";

// 다른 사람의 변경이 왔을 때(SSE) 화면 데이터를 다시 불러오라는 신호
const REFRESH_EVENT = "chakchak:refresh";

interface RefreshDetail {
  topics?: string[];
}

/**
 * 화면 데이터를 다시 불러오라고 알린다.
 *
 * @param topics 없으면 live 데이터를 모두 다시 부른다. 있으면 topic이 없는 데이터(목록, 방 정보처럼 화면에 하나뿐인 것)와
 *     topic이 이 안에 든 데이터만 다시 부른다. 빈 배열이면 topic이 없는 데이터만이다
 */
export function requestRefresh(topics?: string[]) {
  window.dispatchEvent(new CustomEvent<RefreshDetail>(REFRESH_EVENT, { detail: { topics } }));
}

/** 카드처럼 화면에 여러 개 뜨는 데이터의 topic. 실시간 소식이 그 대상만 다시 부르게 한다 */
export const topics = {
  agenda: (agendaId: number) => `agenda:${agendaId}`,
  agendaComments: (agendaId: number) => `agenda-comments:${agendaId}`,
  placeComments: (placeId: number) => `place-comments:${placeId}`,
};

export interface ApiState<T> {
  data: T | undefined;
  error: ApiError | null;
  loading: boolean;
  reload: () => Promise<void>;
  setData: (next: T) => void;
}

/**
 * fetcher를 불러 결과를 들고 있는다. key가 바뀌면 다시 부르고, null이면 부르지 않는다.
 * live가 켜져 있으면 실시간 신호와 창으로 돌아왔을 때도 다시 부른다. topic을 주면 그 topic을 가리킨 신호에만 다시 부른다.
 * 로그인이 풀렸으면(401) 로그인 화면으로 보낸다.
 */
export function useApi<T>(
  key: string | null,
  fetcher: () => Promise<T>,
  options: { live?: boolean; topic?: string } = {},
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
    const topic = options.topic;
    const onRefresh = (e: Event) => {
      const wanted = (e as CustomEvent<RefreshDetail>).detail?.topics;
      if (wanted && topic && !wanted.includes(topic)) return;
      void reload();
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") void reload();
    };
    window.addEventListener(REFRESH_EVENT, onRefresh);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener(REFRESH_EVENT, onRefresh);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [options.live, options.topic, key, reload]);

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

/** 화이트/블랙. 고르지 않으면 시스템 설정을 따른다. 지금 보이는 쪽("light" | "dark")과 바꾸는 함수를 준다 */
export function useTheme(): ["light" | "dark", () => void] {
  const [theme, setTheme] = useState<"light" | "dark">("light");
  useEffect(() => {
    const picked = document.documentElement.dataset.theme;
    if (picked === "dark" || picked === "light") {
      setTheme(picked);
      return;
    }
    // 고른 적이 없으면 시스템 설정을 따르고, 시스템 설정이 바뀌면 같이 바뀐다
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const sync = () => {
      if (!document.documentElement.dataset.theme) setTheme(media.matches ? "dark" : "light");
    };
    sync();
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, []);
  const toggle = useCallback(() => {
    setTheme((current) => {
      const next = current === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      try {
        localStorage.setItem("theme", next);
      } catch {
        // 저장이 막혀 있어도 이번 화면에서는 바뀐다
      }
      return next;
    });
  }, []);
  return [theme, toggle];
}
