"use client";

import { useEffect, useState } from "react";
import { useRoom } from "@/components/room";
import { Avatar, Loading, Notice } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { feedTime, nowKst } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { ChangeLogItemResponse } from "@/lib/types";

export default function NewsPage() {
  const { roomId } = useRoom();
  const first = useApi(`logs:${roomId}`, () => api.changeLogs(roomId, null, 30), { live: true });
  const [older, setOlder] = useState<ChangeLogItemResponse[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 이 화면을 열면 여기까지 봤다고 서버에 알린다
  useEffect(() => {
    void api.seen(roomId, nowKst()).catch(() => undefined);
  }, [roomId]);

  useEffect(() => {
    if (first.data && older.length === 0) setCursor(first.data.nextCursor);
  }, [first.data, older.length]);

  const items = [...(first.data?.items ?? []), ...older.filter((o) => !(first.data?.items ?? []).some((f) => f.logId === o.logId))];

  return (
    <>
      <div className="head">
        <div>
          <h1>새 소식</h1>
          <p>안 본 사이 방에서 바뀐 것을 새것부터 보여 줘요</p>
        </div>
      </div>
      {first.loading && <Loading />}
      {first.error && <Notice kind="err">{first.error.message}</Notice>}
      <section className="card">
        {first.data && items.length === 0 && <div className="empty">아직 소식이 없어요</div>}
        <div className="feed">
          {items.map((it) => (
            <div key={it.logId}>
              {it.actor ? <Avatar name={it.actor.name} /> : <span className="av">착</span>}
              <span>{it.summary}</span>
              <span className="tm">{feedTime(it.createdAt)}</span>
            </div>
          ))}
        </div>
        {cursor && (
          <button
            type="button"
            className="btn btn-line"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                const page = await api.changeLogs(roomId, cursor, 30);
                setOlder((prev) => [...prev, ...page.items]);
                setCursor(page.nextCursor);
              } catch (e) {
                setError(errorMessage(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            지난 소식 더 보기
          </button>
        )}
        {error && <Notice kind="err">{error}</Notice>}
      </section>
    </>
  );
}
