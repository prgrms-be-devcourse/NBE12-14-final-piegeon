import Link from "next/link";

/** 로그인·가입·새 방 화면의 틀. 왼쪽은 문구, 오른쪽은 폼 */
export function AuthShell({
  big,
  sub,
  children,
}: {
  big: React.ReactNode;
  sub: string;
  children: React.ReactNode;
}) {
  return (
    <div className="split">
      <section className="split-art" aria-label="착착 소개">
        <Link href="/" className="brand">
          착착
        </Link>
        <div className="stack">
          <p className="big">{big}</p>
          <p style={{ fontSize: 16 }}>{sub}</p>
        </div>
      </section>
      <main className="split-form">
        <div>{children}</div>
      </main>
    </div>
  );
}
