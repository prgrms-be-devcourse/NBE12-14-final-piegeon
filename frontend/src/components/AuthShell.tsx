import Link from "next/link";
import { PHOTOS, type PhotoKey } from "@/lib/photos";

/** 로그인·가입·새 방 화면의 틀. 왼쪽은 여행 사진과 문구, 오른쪽은 폼 */
export function AuthShell({
  photo,
  big,
  sub,
  children,
}: {
  photo: PhotoKey;
  big: React.ReactNode;
  sub: string;
  children: React.ReactNode;
}) {
  const p = PHOTOS[photo];
  return (
    <div className="split">
      <section className="split-art" aria-label="여행 사진">
        {/* 사진은 장식이 아니라 출처를 밝혀야 하는 작품이라 alt와 출처를 함께 둔다 */}
        <img className="bg" src={p.src} alt={p.alt} />
        <span className="scrim" aria-hidden="true" />
        <Link href="/" className="brand">
          착착
        </Link>
        <div className="stack">
          <p className="big">{big}</p>
          <p style={{ fontSize: 16 }}>{sub}</p>
          <Link href="/credits" className="credit">
            사진: {p.title}, {p.author}, {p.license}
          </Link>
        </div>
      </section>
      <main className="split-form">
        <div>{children}</div>
      </main>
    </div>
  );
}
