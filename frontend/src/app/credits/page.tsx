import { TopBar } from "@/components/TopBar";
import { PHOTOS } from "@/lib/photos";

export const metadata = { title: "사진·글꼴 출처 | 착착" };

export default function CreditsPage() {
  return (
    <>
      <TopBar loggedIn={false} />
      <main className="page" style={{ maxWidth: 760 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 32 }}>사진·글꼴 출처</h1>
          <p className="muted" style={{ marginTop: 6 }}>
            착착에 쓴 사진은 모두 Wikimedia Commons에서 가져와 크기를 줄였어요. 각 라이선스 조건에 따라 저작자와 라이선스를 밝혀요.
          </p>
        </div>
        <section className="card" aria-labelledby="photos-h">
          <h2 className="h3" id="photos-h">
            사진
          </h2>
          <ul className="stack" style={{ margin: 0, paddingLeft: 0, listStyle: "none", gap: 14 }}>
            {Object.values(PHOTOS).map((p) => (
              <li key={p.src} className="row" style={{ flexWrap: "nowrap", gap: 14 }}>
                <img src={p.src} alt="" width={72} height={54} style={{ objectFit: "cover", borderRadius: 10, flex: "none" }} />
                <span className="stack" style={{ gap: 0 }}>
                  <b>{p.title}</b>
                  <span className="small muted">
                    {p.author}, Wikimedia Commons,{" "}
                    <a href={p.licenseUrl} target="_blank" rel="noopener noreferrer">
                      {p.license}
                    </a>
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </section>
        <section className="card" aria-labelledby="font-h">
          <h2 className="h3" id="font-h">
            글꼴
          </h2>
          <p className="small">
            Pretendard, Kil Hyung-jin,{" "}
            <a href="https://openfontlicense.org" target="_blank" rel="noopener noreferrer">
              SIL Open Font License 1.1
            </a>
          </p>
        </section>
      </main>
    </>
  );
}
