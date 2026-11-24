"use client";

import { useState } from "react";
import { useRoom } from "@/components/room";
import { Avatar, Faces, Loading, Notice } from "@/components/ui";
import { ApiError, api, errorMessage } from "@/lib/api";
import { feedTime } from "@/lib/format";
import { requestRefresh, useApi } from "@/lib/hooks";
import type { PlaceCandidateItem, PlaceDetailResponse, PlaceSearchItem, PlaceStage } from "@/lib/types";

const COLUMNS: { stage: PlaceStage; title: string; desc: string }[] = [
  { stage: "COMPLEMENT", title: "한 명만 원해요", desc: "올린 사람만 가고 싶어요" },
  { stage: "PARTIAL", title: "몇 명이 좋아해요", desc: "두 명 이상 가고 싶어요" },
  { stage: "FULL", title: "다 같이 가고 싶어요", desc: "확정하면 일정에 넣을 수 있어요" },
  { stage: "CONFIRMED", title: "확정", desc: "일정에 넣을 수 있는 곳" },
];

export default function PlacesPage() {
  const { roomId, room } = useRoom();
  const board = useApi(`board:${roomId}`, () => api.placeBoard(roomId), { live: true });
  const [selected, setSelected] = useState<number | null>(null);
  const editable = room.status === "PLANNING";
  const candidates = board.data?.candidates ?? [];
  const selectedItem = candidates.find((c) => c.placeId === selected) ?? null;

  return (
    <>
      <div className="head">
        <div>
          <h1>가고 싶은 곳</h1>
          <p>카드를 눌러 가고 싶은지 표시하고 댓글을 남겨요. 원하는 사람이 많을수록 오른쪽으로 옮겨가고, 다 같이 원하는 곳을 확정하면 일정에 넣을 수 있어요</p>
        </div>
        {board.data && (
          <span className="small muted">
            <b style={{ color: "var(--ink)" }}>{candidates.length}</b> / {board.data.limit}곳
          </span>
        )}
      </div>

      {!editable && (
        <Notice>
          {room.status === "SURVEYING"
            ? "같이 정하기가 끝나면 장소를 올릴 수 있어요. 지금은 둘러보고 댓글만 남길 수 있어요."
            : "지금 단계에서는 장소를 올리거나 표시를 바꿀 수 없어요."}
        </Notice>
      )}
      {editable && board.data && (
        <PlaceSearch
          full={candidates.length >= board.data.limit}
          onRegistered={(placeId) => {
            void board.reload();
            setSelected(placeId);
          }}
        />
      )}

      {board.loading && <Loading />}
      {board.error && <Notice kind="err">{board.error.message}</Notice>}
      {board.data && (
        <div className="with-panel">
          <div className="kb grow">
            {COLUMNS.map((col) => {
              const items = candidates.filter((c) => c.stage === col.stage);
              return (
                <section key={col.stage} className="col" aria-label={col.title}>
                  <div className="col-h">
                    <span className="r">
                      <b>{col.title}</b>
                      <span className="n">{items.length}</span>
                    </span>
                    <p>{col.desc}</p>
                  </div>
                  {items.map((c) => (
                    <PlaceCard key={c.placeId} item={c} active={selected === c.placeId} onOpen={() => setSelected(c.placeId)} />
                  ))}
                </section>
              );
            })}
          </div>
          {selectedItem && (
            <PlacePanel
              key={selectedItem.placeId}
              item={selectedItem}
              onClose={() => setSelected(null)}
              onChanged={() => void board.reload()}
              onDeleted={() => {
                setSelected(null);
                void board.reload();
              }}
            />
          )}
        </div>
      )}
      {board.data && candidates.length === 0 && (
        <div className="card empty">
          <b>아직 올라온 곳이 없어요</b>
          <span>{editable ? "위에서 장소를 찾아 첫 후보를 올려 보세요" : "같이 정하기가 끝나면 장소를 모아요"}</span>
        </div>
      )}
    </>
  );
}

function PlaceCard({ item, active, onOpen }: { item: PlaceCandidateItem; active: boolean; onOpen: () => void }) {
  return (
    <button type="button" className="pcard" aria-current={active} onClick={onOpen} aria-label={`${item.name} 자세히 보기`}>
      {item.stage === "CONFIRMED" && <span className="stamp" aria-hidden="true">확정</span>}
      <span className="stack" style={{ gap: 2, paddingRight: item.stage === "CONFIRMED" ? 40 : 0 }}>
        <span className="nm">{item.name}</span>
        <span className="meta">{item.address}</span>
      </span>
      <span className="who">
        {item.agreedBy.length > 0 && <Faces names={item.agreedBy.map((m) => m.name)} />}
        <span>
          {item.agreeCount}명 가고 싶어요
          {item.nopeCount > 0 && `, ${item.nopeCount}명 별로예요`}
        </span>
      </span>
      <span className="small muted">{item.suggestedBy.name}님이 올렸어요</span>
    </button>
  );
}

function PlaceSearch({ full, onRegistered }: { full: boolean; onRegistered: (placeId: number) => void }) {
  const { roomId } = useRoom();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceSearchItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function search(e: React.FormEvent) {
    e.preventDefault();
    const q = query.trim();
    if (q.length < 2) return setError("두 글자 이상 넣어 주세요.");
    setBusy(true);
    setError(null);
    try {
      setResults((await api.searchPlaces(roomId, q)).places);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function register(p: PlaceSearchItem) {
    setBusy(true);
    setError(null);
    try {
      const created = await api.registerPlace(roomId, p.googlePlaceId);
      setResults(null);
      setQuery("");
      requestRefresh();
      onRegistered(created.placeId);
    } catch (err) {
      if (err instanceof ApiError && err.code === "PLACE_ALREADY_REGISTERED") {
        const data = err.data as { placeId?: number } | undefined;
        if (data?.placeId) onRegistered(data.placeId);
      }
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card" aria-label="장소 찾기" style={{ gap: 10 }}>
      <form className="row" style={{ flexWrap: "nowrap" }} onSubmit={search}>
        <label htmlFor="q" className="sr-only">
          장소 찾기
        </label>
        <input id="q" className="in grow" placeholder="Google 지도에서 장소 찾기 (예: 구로몬 시장)" maxLength={50} value={query} onChange={(e) => setQuery(e.target.value)} />
        <button type="submit" className="btn btn-accent" disabled={busy || full}>
          찾기
        </button>
      </form>
      {full && <p className="small muted">후보가 꽉 찼어요. 한 명만 원하는 곳을 정리하면 더 올릴 수 있어요</p>}
      {error && <Notice kind="err">{error}</Notice>}
      {results && (
        <ul className="stack" style={{ listStyle: "none", margin: 0, padding: 0, gap: 6 }}>
          {results.length === 0 && <li className="muted">찾은 곳이 없어요. 다른 이름으로 찾아 보세요</li>}
          {results.map((p) => (
            <li key={p.googlePlaceId} className="row" style={{ flexWrap: "nowrap", padding: "8px 0", borderTop: "1px solid var(--line)" }}>
              <span className="stack grow" style={{ gap: 0 }}>
                <b>{p.name}</b>
                <span className="small muted">{p.address}</span>
              </span>
              <button type="button" className="btn btn-line btn-sm" disabled={busy || p.alreadyRegistered || full} onClick={() => register(p)}>
                {p.alreadyRegistered ? "이미 올라왔어요" : "올리기"}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function PlacePanel({
  item,
  onClose,
  onChanged,
  onDeleted,
}: {
  item: PlaceCandidateItem;
  onClose: () => void;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  const { roomId, room, myMember } = useRoom();
  // 보드가 바뀌면(표시 수, 단계) 상세도 다시 불러온다
  const detail = useApi(`place:${roomId}:${item.placeId}:${item.agreeCount}:${item.nopeCount}:${item.stage}`, () =>
    api.place(roomId, item.placeId),
  );
  const comments = useApi(`place-comments:${roomId}:${item.placeId}`, () => api.placeComments(roomId, item.placeId), { live: true });
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [photoFailed, setPhotoFailed] = useState(false);

  const d: PlaceDetailResponse | undefined = detail.data;
  const editable = room.status === "PLANNING" && item.stage !== "CONFIRMED";
  const canComment = room.status === "SURVEYING" || room.status === "PLANNING" || room.status === "REVIEWING";
  const mine = d?.reactions.find((r) => r.memberId === myMember?.memberId)?.reaction?.value ?? null;

  async function run<T>(action: () => Promise<T>, after?: (result: T) => void) {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      after?.(result);
      requestRefresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function react(value: "WANT" | "NOPE") {
    void run(
      () => (mine === value ? api.cancelReaction(roomId, item.placeId) : api.react(roomId, item.placeId, value)),
      (next) => {
        detail.setData(next);
        onChanged();
      },
    );
  }

  const stageTitle = COLUMNS.find((c) => c.stage === item.stage)?.title;
  const commentList = comments.data?.comments ?? [];

  return (
    <aside className="panel" aria-label="장소 자세히 보기">
      <div className="p-top">
        <span className={item.stage === "CONFIRMED" ? "tag ok" : "tag"}>{stageTitle}</span>
        <button type="button" className="btn btn-ghost" onClick={onClose} aria-label="닫기">
          닫기
        </button>
      </div>
      <div className="p-body">
        {d?.photoUrl && !photoFailed && (
          // 서버가 Google 사진 주소로 넘겨준다(302). 키는 브라우저에 보이지 않는다
          <img className="photo" src={d.photoUrl} alt={`${item.name} 사진`} onError={() => setPhotoFailed(true)} />
        )}
        <div className="stack" style={{ gap: 4 }}>
          <h2 className="h2">{item.name}</h2>
          <p className="small muted">
            {d?.category ? `${d.category.label}, ` : ""}
            {item.address}
          </p>
          <p className="small muted">{item.suggestedBy.name}님이 올렸어요</p>
          {d?.googleMapsUrl && (
            <a href={d.googleMapsUrl} target="_blank" rel="noopener noreferrer" className="small">
              Google 지도에서 열기
            </a>
          )}
        </div>

        {editable && (
          <section className="stack" aria-label="내 표시" style={{ gap: 8 }}>
            <h3 className="h3">나는 여기</h3>
            <div className="row">
              <button type="button" className="chip" aria-pressed={mine === "WANT"} disabled={busy || !myMember} onClick={() => react("WANT")}>
                가고 싶어요
              </button>
              <button type="button" className="chip" aria-pressed={mine === "NOPE"} disabled={busy || !myMember} onClick={() => react("NOPE")}>
                별로예요
              </button>
            </div>
            {mine && <span className="small muted">한 번 더 누르면 표시를 지워요</span>}
          </section>
        )}

        {d && (
          <section className="stack" aria-label="친구들 표시" style={{ gap: 8 }}>
            <h3 className="h3">
              친구들은 ({d.agreeCount} / {d.activeMemberCount}명 가고 싶어요)
            </h3>
            {d.reactions.map((r) => (
              <div key={r.memberId} className="row" style={{ flexWrap: "nowrap" }}>
                <Avatar name={r.name} />
                <span className="grow">
                  {r.name}
                  {r.memberId === myMember?.memberId && " (나)"}
                </span>
                <span className={r.reaction?.value === "WANT" ? "tag" : "tag n"}>{r.reaction?.label ?? "아직"}</span>
              </div>
            ))}
          </section>
        )}

        {room.status === "PLANNING" && item.stage === "FULL" && (
          <div className="notice ok stack" style={{ gap: 8 }}>
            <span>모두 가고 싶어해요. 확정하면 일정에 넣을 수 있어요</span>
            <button type="button" className="btn btn-accent" disabled={busy} onClick={() => run(() => api.confirmPlace(roomId, item.placeId), onChanged)}>
              확정하기
            </button>
          </div>
        )}
        {item.stage === "CONFIRMED" && <Notice kind="ok">확정된 곳이에요. 일정에서 원하는 날에 넣어요</Notice>}

        {error && <Notice kind="err">{error}</Notice>}

        <section className="stack" aria-label="댓글" style={{ gap: 10 }}>
          <h3 className="h3">댓글 {commentList.length}</h3>
          {comments.data && commentList.length === 0 && <p className="small muted">아직 댓글이 없어요. 첫 댓글을 남겨 보세요</p>}
          {commentList.map((c) => (
            <div key={c.commentId} className="cmt">
              <Avatar name={c.name} />
              <div className="bub">
                <div className="h">
                  <b>{c.name}</b>
                  {feedTime(c.createdAt)}
                </div>
                <p>{c.body}</p>
              </div>
            </div>
          ))}
        </section>

        {editable && (
          <button
            type="button"
            className="btn btn-danger btn-sm"
            style={{ alignSelf: "flex-start" }}
            disabled={busy}
            onClick={() => {
              if (!window.confirm(`${item.name}을(를) 후보에서 지울까요? 표시와 댓글도 함께 지워져요.`)) return;
              void run(() => api.deletePlace(roomId, item.placeId), onDeleted);
            }}
          >
            후보에서 지우기
          </button>
        )}
      </div>
      {canComment && (
        <form
          className="p-foot"
          onSubmit={(e) => {
            e.preventDefault();
            if (!body.trim()) return;
            void run(
              () => api.addPlaceComment(roomId, item.placeId, body.trim()),
              (c) => {
                comments.setData({ comments: [...commentList, c] });
                setBody("");
              },
            );
          }}
        >
          <label htmlFor="cmt-in" className="sr-only">
            댓글 쓰기
          </label>
          <input id="cmt-in" className="in grow" maxLength={500} placeholder="댓글 쓰기" value={body} onChange={(e) => setBody(e.target.value)} />
          <button type="submit" className="btn btn-accent" disabled={busy || !body.trim()}>
            남기기
          </button>
        </form>
      )}
    </aside>
  );
}
