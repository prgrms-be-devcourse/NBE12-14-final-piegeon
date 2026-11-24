import Link from "next/link";
import { TopBar } from "@/components/TopBar";
import { PHOTOS } from "@/lib/photos";

// 랜딩의 예시는 모두 넷이서 가는 오사카 여행 한 건이다. 화면 조각은 실제 방 안 화면과 같은 모양으로 그린다.

const CHAT = [
  { who: "동준", text: "숙소 후보 다섯 개 올렸어! 어디가 좋아?" },
  { who: "서연", text: "다 좋아 ㅎㅎ" },
  { who: "민재", text: "네가 정해줘" },
  { who: null, text: "그럼 여기로 예약할게" },
  { who: "하린", text: "아 근데 나 1박 8만원 넘으면 좀 부담돼…", late: true },
];

const PLACES = [
  { photo: PHOTOS.castle, name: "오사카성 공원", note: "1일차 오후", chip: "확정", on: true },
  { photo: PHOTOS.umeda, name: "우메다 공중정원", note: "넷 다 가고 싶대요", chip: "다 같이" },
  { photo: PHOTOS.kaiyukan, name: "카이유칸 수족관", note: "민재는 별로래요", chip: "3명" },
  { photo: PHOTOS.hozenji, name: "호젠지 요코초", note: "서연이 올렸어요", chip: "새로 올라옴" },
  { photo: PHOTOS.kuromon, name: "구로몬 시장", note: "1일차 점심", chip: "확정", on: true },
];

function Faces({ names }: { names: string[] }) {
  return (
    <span className="land-faces" aria-label={names.join(", ")}>
      {names.map((n) => (
        <span key={n} className="av" aria-hidden="true">
          {n}
        </span>
      ))}
    </span>
  );
}

export default function Landing() {
  return (
    <div className="land">
      <TopBar loggedIn={false} />

      <section className="land-in land-hero" aria-labelledby="hero-h">
        <div>
          <h1 id="hero-h">
            친구 넷,
            <br />
            취향도 넷,
            <br />
            <em>여행은 하나.</em>
          </h1>
          <p className="land-lead">착착은 서로 바라는 걸 먼저 모으고, 갈리는 것만 같이 정해요. 정해진 건 그대로 일정이 돼요.</p>
          <div className="land-cta">
            <Link href="/rooms/new" className="btn btn-accent">
              여행방 만들기
            </Link>
            <Link href="/rooms" className="btn btn-line">
              초대받았어요
            </Link>
          </div>
          <p className="land-note">링크 하나로 친구를 부르면, 들어오자마자 설문부터 시작해요</p>
        </div>
        <figure className="land-shot">
          <img src={PHOTOS.dotonbori.src} alt={PHOTOS.dotonbori.alt} width={960} height={1175} />
          <figcaption aria-label="같이 정하기 화면 예시">
            <span className="q">
              하루 속도<span>2 대 2</span>
            </span>
            <span className="land-opts">
              <span className="land-opt">
                여유롭게
                <Faces names={["서", "하"]} />
              </span>
              <span className="land-opt on">
                적당히
                <Faces names={["동", "민"]} />
              </span>
            </span>
            <span className="small muted">갈린 것만 이렇게 같이 정해요</span>
          </figcaption>
        </figure>
      </section>

      <section className="land-night land-sec" aria-labelledby="why-h">
        <div className="land-in land-two">
          <div>
            <h2 id="why-h" className="land-h2">
              단톡방에서 짜면,
              <br />늘 한 명이 다 짜요
            </h2>
            <p className="land-lead" style={{ marginTop: 22 }}>
              &lsquo;아무거나 좋아&rsquo;가 오가다가 다 정하고 나서야 반대가 나와요. 밀린 대화는 아무도 안 읽고, 예약한 곳과 후보가 섞여요.
            </p>
          </div>
          <div className="land-chat" aria-label="단톡방 대화 예시">
            {CHAT.map((m, i) =>
              m.who ? (
                <div key={i}>
                  <p className="who">{m.who}</p>
                  <span className={m.late ? "bub late" : "bub"}>{m.text}</span>
                </div>
              ) : (
                <span key={i} className="bub me">
                  {m.text}
                </span>
              ),
            )}
          </div>
        </div>
      </section>

      <section id="how" className="land-sec alt" aria-labelledby="how-h">
        <div className="land-in">
          <h2 id="how-h" className="land-h2">
            짜기 전에 먼저 맞추고,
            <br />
            맞춘 대로 짜요
          </h2>
          <ol className="land-steps" style={{ listStyle: "none", padding: 0 }}>
            <li className="land-step">
              <div className="land-step-tx">
                <span className="land-eyebrow">1 설문</span>
                <h3>바라는 것부터 물어봐요</h3>
                <p>예산, 숙소, 하루 속도, 하고 싶은 것과 피하고 싶은 것. 모두 같은 답은 바로 정해져요.</p>
              </div>
              <div className="land-ui" aria-hidden="true">
                <span className="ttl">1인 예산은요?</span>
                <span className="land-grid4">
                  <span className="land-tile">100만원</span>
                  <span className="land-tile on">140만원</span>
                  <span className="land-tile">170만원</span>
                  <span className="land-tile">200만원 이상</span>
                </span>
                <span className="sub">4명 중 3명이 답했어요</span>
              </div>
            </li>
            <li className="land-step">
              <div className="land-step-tx">
                <span className="land-eyebrow">2 같이 정하기</span>
                <h3>갈린 것만 투표해요</h3>
                <p>모두 같은 걸 고를 때까지 의견을 나눠요. 누가 뭘 골랐는지 다 보여요.</p>
              </div>
              <div className="land-ui" aria-hidden="true">
                <span className="ttl">
                  하루 속도<span className="tag">내 선택이 필요해요</span>
                </span>
                <span className="land-grid4">
                  <span className="land-tile">
                    여유롭게
                    <br />
                    <Faces names={["서", "하"]} />
                  </span>
                  <span className="land-tile on">
                    적당히
                    <br />
                    <Faces names={["동", "민"]} />
                  </span>
                </span>
                <span className="sub">서연: &ldquo;첫날은 비행기 때문에 좀 쉬엄쉬엄 가자&rdquo;</span>
              </div>
            </li>
            <li className="land-step">
              <div className="land-step-tx">
                <span className="land-eyebrow">3 가고 싶은 곳</span>
                <h3>원하는 사람이 많을수록 앞으로</h3>
                <p>각자 가고 싶은 곳을 올리고 &lsquo;가고 싶어요&rsquo;를 눌러요. 모두 원하는 곳은 확정해요.</p>
              </div>
              <div className="land-ui" aria-hidden="true">
                <span className="land-cols">
                  <span className="land-col">
                    <span>1명</span>
                    <img src={PHOTOS.usj.src} alt="" width={960} height={1280} />
                    <b>유니버설 스튜디오</b>
                  </span>
                  <span className="land-col">
                    <span>3명</span>
                    <img src={PHOTOS.kushikatsu.src} alt="" width={960} height={720} />
                    <b>신세카이 쿠시카츠</b>
                  </span>
                  <span className="land-col full">
                    <span>모두</span>
                    <img src={PHOTOS.kuromon.src} alt="" width={960} height={640} />
                    <b>구로몬 시장</b>
                  </span>
                </span>
              </div>
            </li>
            <li className="land-step">
              <div className="land-step-tx">
                <span className="land-eyebrow">4 일정 확정</span>
                <h3>모두 동의하면 한 장으로</h3>
                <p>확정한 장소를 하루 10칸에 배치하고, 모두 동의하면 일정이 확정돼요.</p>
              </div>
              <div className="land-ui" aria-hidden="true">
                <span className="ttl">
                  1일차 · 토요일<span className="tag ok">확정</span>
                </span>
                <ul className="land-day">
                  <li>
                    <time>12:30</time>구로몬 시장
                  </li>
                  <li>
                    <time>15:00</time>오사카성 공원
                  </li>
                  <li>
                    <time>18:30</time>도톤보리 저녁
                  </li>
                </ul>
                <span className="land-agree">
                  넷 다 동의했어요
                  <Faces names={["동", "서", "민", "하"]} />
                </span>
              </div>
            </li>
          </ol>
        </div>
      </section>

      <section className="land-sec" aria-labelledby="pl-h">
        <div className="land-in">
          <h2 id="pl-h" className="land-h2">
            넷이 모은 오사카
          </h2>
          <p className="land-lead" style={{ marginTop: 16, maxWidth: "none" }}>
            누가 올렸고 몇 명이 원하는지, 사진과 함께 한눈에 봐요
          </p>
          <div className="land-places">
            {PLACES.map((p) => (
              <figure key={p.name} className="land-place">
                <img src={p.photo.src} alt={p.photo.alt} width={480} height={640} loading="lazy" />
                <span className={p.on ? "chip on" : "chip"}>{p.chip}</span>
                <figcaption>
                  <b>{p.name}</b>
                  <span>{p.note}</span>
                </figcaption>
              </figure>
            ))}
          </div>
        </div>
      </section>

      <section className="land-sec alt" aria-labelledby="news-h">
        <div className="land-in land-two">
          <div>
            <h2 id="news-h" className="land-h2">
              이틀 만에 들어와도
              <br />
              바로 따라잡아요
            </h2>
            <p className="land-lead" style={{ marginTop: 22 }}>
              밀린 대화를 거슬러 올라갈 필요 없어요. 안 본 사이 바뀐 것만, 내가 해야 할 것부터 보여줘요.
            </p>
          </div>
          <div className="land-news" aria-label="새 소식 예시">
            <ul>
              <li>
                <span className="ic must" aria-hidden="true">
                  !
                </span>
                <span>
                  <b>하루 속도, 내 선택이 필요해요</b>
                  <small>2 대 2로 갈렸어요</small>
                </span>
              </li>
              <li>
                <span className="ic av" aria-hidden="true" style={{ width: 48, height: 48, fontSize: 16 }}>
                  하
                </span>
                <span>
                  <b>호젠지 요코초, 이제 3명이 원해요</b>
                  <small>하린도 가고 싶대요</small>
                </span>
              </li>
              <li>
                <img className="ic" src={PHOTOS.kuromon.src} alt="" width={48} height={48} />
                <span>
                  <b>구로몬 시장이 확정됐어요</b>
                  <small>1일차 점심에 들어갔어요</small>
                </span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section className="land-end" aria-labelledby="end-h">
        <div className="land-in">
          <div>
            <h2 id="end-h" className="land-h2">
              다음 여행,
              <br />
              착착 맞춰 봐요
            </h2>
            <p>방을 만들고 링크만 보내면 돼요</p>
          </div>
          <Link href="/rooms/new" className="btn">
            여행방 만들기
          </Link>
        </div>
      </section>

      <footer className="land-in land-foot">
        <span className="brand">착착</span>
        <span>
          사진은 Wikimedia Commons(CC BY, CC BY-SA)에서 가져왔어요. <Link href="/credits">사진·글꼴 출처</Link>
        </span>
      </footer>
    </div>
  );
}
