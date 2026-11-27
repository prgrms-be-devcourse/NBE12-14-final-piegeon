// 서버의 시각은 오프셋 없는 한국 시각이다. 브라우저 시간대와 상관없이 글자 그대로 한국 시각으로 읽고 보여 준다.
// 계산은 "한국 벽시계 숫자"를 UTC 칸에 넣은 값끼리 한다. 그래서 두 시각의 차이는 시간대와 상관없이 맞다.

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

interface Parts {
  y: number;
  mo: number;
  d: number;
  h: number;
  mi: number;
}

function parts(value: string): Parts {
  const [date, time = "00:00"] = value.split("T");
  const [y, mo, d] = date.split("-").map(Number);
  const [h, mi] = time.split(":").map(Number);
  return { y, mo, d, h: h || 0, mi: mi || 0 };
}

function toEpoch(p: Parts): number {
  return Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi);
}

const kstFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

/** 지금 한국 시각, "YYYY-MM-DDTHH:mm:ss" (오프셋 없음). 확인 시각(seen) 요청에 쓴다 */
export function nowKst(): string {
  const p = Object.fromEntries(kstFormatter.formatToParts(new Date()).map((x) => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}`;
}

/** 오늘 한국 날짜, "YYYY-MM-DD" */
export function todayKst(): string {
  return nowKst().slice(0, 10);
}

/** "YYYY-MM-DD"에 days일을 더한다 */
export function addDays(date: string, days: number): string {
  const t = new Date(toEpoch(parts(date)) + days * 86_400_000);
  return t.toISOString().slice(0, 10);
}

export function weekday(date: string): string {
  return WEEKDAYS[new Date(toEpoch(parts(date))).getUTCDay()];
}

/** "12월 18일 (금)" */
export function dateLabel(date: string): string {
  const p = parts(date);
  return `${p.mo}월 ${p.d}일 (${weekday(date)})`;
}

/** "12월 18일 금요일" */
export function longDate(date: string): string {
  const p = parts(date);
  return `${p.mo}월 ${p.d}일 ${weekday(date)}요일`;
}

/** "12.18 금" */
export function shortDate(date: string): string {
  const p = parts(date);
  return `${p.mo}.${p.d} ${weekday(date)}`;
}

/** 여행 일수 (끝 날 포함) */
export function tripDays(start: string, end: string): number {
  return Math.round((toEpoch(parts(end)) - toEpoch(parts(start))) / 86_400_000) + 1;
}

/** "2박 3일", 하루짜리면 "당일" */
export function tripLength(start: string, end: string): string {
  const days = tripDays(start, end);
  return days <= 1 ? "당일" : `${days - 1}박 ${days}일`;
}

/** "D-37", 당일 "D-DAY", 지났으면 "다녀왔어요" */
export function dDay(start: string, end?: string): string {
  const today = todayKst();
  const diff = Math.round((toEpoch(parts(start)) - toEpoch(parts(today))) / 86_400_000);
  if (diff > 0) return `D-${diff}`;
  if (diff === 0) return "D-DAY";
  if (end && toEpoch(parts(end)) >= toEpoch(parts(today))) return "여행 중";
  return "다녀왔어요";
}

function clock(p: Parts): string {
  const ampm = p.h < 12 ? "오전" : "오후";
  const h = p.h % 12 === 0 ? 12 : p.h % 12;
  return `${ampm} ${h}:${String(p.mi).padStart(2, "0")}`;
}

/** "11월 17일 (화) 오후 3:00" */
export function dateTimeLabel(value: string): string {
  return `${dateLabel(value.slice(0, 10))} ${clock(parts(value))}`;
}

/** 소식에 쓰는 짧은 시각: "오늘 오전 9:41", "어제 오후 11:40", "11월 3일" */
export function feedTime(value: string): string {
  const day = value.slice(0, 10);
  const today = todayKst();
  if (day === today) return `오늘 ${clock(parts(value))}`;
  if (day === addDays(today, -1)) return `어제 ${clock(parts(value))}`;
  const p = parts(value);
  return `${p.mo}월 ${p.d}일`;
}

/** 마감까지 남은 시간: "5일 20시간 남음", "3시간 남음", "마감됐어요" */
export function remaining(deadline: string): string {
  const ms = toEpoch(parts(deadline)) - toEpoch(parts(nowKst()));
  if (ms <= 0) return "마감됐어요";
  const hours = Math.floor(ms / 3_600_000);
  const days = Math.floor(hours / 24);
  if (days > 0) return `${days}일 ${hours % 24}시간 남음`;
  if (hours > 0) return `${hours}시간 남음`;
  return `${Math.max(1, Math.floor(ms / 60_000))}분 남음`;
}

/** 원 → "140만원". 만원 아래가 있으면 "1,234,500원" */
export function won(amount: number): string {
  if (amount % 10_000 === 0) return `${(amount / 10_000).toLocaleString("ko-KR")}만원`;
  return `${amount.toLocaleString("ko-KR")}원`;
}

/** 이름의 첫 글자 (아바타) */
export function initial(name: string): string {
  return Array.from(name.trim())[0] ?? "?";
}

/** 이름 뒤에 을/를을 붙인다. 받침이 있으면 "을", 없으면 "를". 한글로 끝나지 않으면 "을(를)" */
export function withObject(word: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return `${word}을(를)`;
  return `${word}${code % 28 === 0 ? "를" : "을"}`;
}
