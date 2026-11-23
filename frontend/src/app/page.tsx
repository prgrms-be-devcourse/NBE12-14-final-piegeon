import Link from "next/link";
import { TopBar } from "@/components/TopBar";

const STEPS = [
  { n: "1 설문", t: "바라는 것부터 물어봐요", d: "예산, 숙소, 속도, 하고 싶은 것과 피하고 싶은 것. 모두 같은 답은 바로 정해져요." },
  { n: "2 같이 정하기", t: "갈린 것만 투표해요", d: "모두 같은 걸 고를 때까지 의견을 나눠요. 누가 뭘 골랐는지 다 보여요." },
  { n: "3 가고 싶은 곳", t: "표시하면 카드가 움직여요", d: "원하는 사람이 많을수록 오른쪽으로. 모두 원하면 확정해요." },
  { n: "4 일정 확정", t: "모두 동의하면 한 장으로", d: "하루 10칸을 채우고 모두 동의하면 확정돼요." },
];

export default function Landing() {
  return (
    <>
      <TopBar loggedIn={false} />
      <section className="land-hero">
        <h1>
          친구 넷, 취향도 넷,
          <br />
          여행은 하나.
        </h1>
        <p>착착은 서로 바라는 걸 먼저 모으고, 갈리는 것만 같이 정해요. 정해진 건 그대로 일정이 돼요.</p>
        <div className="row" style={{ gap: 10 }}>
          <Link href="/rooms/new" className="btn btn-accent btn-lg">
            여행방 만들기
          </Link>
          <Link href="/rooms" className="btn btn-line btn-lg">
            초대받았어요
          </Link>
        </div>
      </section>
      <section className="land-steps" aria-label="착착 사용 순서">
        {STEPS.map((s) => (
          <div key={s.n} className="card">
            <b className="n">{s.n}</b>
            <h2 className="h3">{s.t}</h2>
            <p className="small muted">{s.d}</p>
          </div>
        ))}
      </section>
    </>
  );
}
